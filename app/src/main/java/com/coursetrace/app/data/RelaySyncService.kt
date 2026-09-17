package com.coursetrace.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coursetrace.app.CourseTraceApplication
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Serializable
data class RelayChangeDto(
    val sequence: Long,
    val id: String,
    val type: String,
    val createdAt: String,
    val payload: JsonObject,
)

@Serializable
private data class RelayChangesResponse(val changes: List<RelayChangeDto>)

@Serializable
private data class RelayUpcoming(
    val ownerId: String,
    val ownerType: String,
    val title: String,
    val start: String,
    val end: String,
    val room: String,
    val teacher: String,
)

@Serializable
private data class RelayMaterial(
    val id: String,
    val ownerId: String,
    val title: String,
    val kind: String,
    val week: Int?,
    val chapter: String,
)

@Serializable
private data class RelayRecord(
    val id: String,
    val ownerId: String,
    val sessionId: String,
    val timestamp: String,
    val kind: String,
    val content: String,
)

@Serializable
private data class RelaySnapshot(
    val updatedAt: String,
    val deviceId: String,
    val upcoming: List<RelayUpcoming>,
    val materials: List<RelayMaterial>,
    val records: List<RelayRecord>,
)

class RelaySyncService(
    private val repository: AppRepository,
    private val secureSettings: SecureSettings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val state = repository.state.value
            val link = state.preferences.chatGptLink
            require(link.enabled) { "ChatGPT 联动尚未启用" }
            require(link.relayUrl.startsWith("https://")) {
                "中继必须使用 HTTPS"
            }
            val token = secureSettings.getRelayToken().orEmpty()
            require(token.length >= 32) { "配对令牌无效" }

            val relaySnapshot = snapshot(state)
            val snapshotBody = json.encodeToString(relaySnapshot)
            val snapshotHash = MessageDigest.getInstance("SHA-256")
                .digest(json.encodeToString(relaySnapshot.copy(updatedAt = "")).toByteArray())
                .joinToString("") { "%02x".format(it) }
            if (snapshotHash != secureSettings.getRelaySnapshotHash()) {
                request(
                    method = "PUT",
                    url = link.relayUrl.trimEnd('/') + "/v1/device/snapshot",
                    token = token,
                    body = snapshotBody,
                )
                secureSettings.setRelaySnapshotHash(snapshotHash)
            }
            val response = request(
                method = "GET",
                url = link.relayUrl.trimEnd('/') + "/v1/device/changes?after=${link.lastSequence}",
                token = token,
            )
            val changes = json.decodeFromString<RelayChangesResponse>(response).changes
            if (changes.isNotEmpty()) {
                repository.applyRelayChanges(changes)
                val latest = changes.maxOf { it.sequence }
                request(
                    method = "POST",
                    url = link.relayUrl.trimEnd('/') + "/v1/device/ack",
                    token = token,
                    body = "{\"through\":$latest}",
                )
                val current = repository.state.value.preferences
                repository.updatePreferences(current.copy(
                    chatGptLink = current.chatGptLink.copy(
                        lastSequence = latest,
                        lastSyncAt = OffsetDateTime.now().toString(),
                    ),
                ))
            }
            changes.size
        }
    }

    private fun snapshot(state: AppState): RelaySnapshot {
        val sessions = state.sessions.associateBy { it.id }
        return RelaySnapshot(
            updatedAt = OffsetDateTime.now().toString(),
            deviceId = state.preferences.chatGptLink.deviceId,
            upcoming = ScheduleEngine.upcoming(state, days = 28).take(200).map {
                RelayUpcoming(
                    ownerId = it.course.id,
                    ownerType = "course",
                    title = it.course.name,
                    start = it.start.atOffset(OffsetDateTime.now().offset).toString(),
                    end = it.end.atOffset(OffsetDateTime.now().offset).toString(),
                    room = it.room,
                    teacher = it.course.teacher,
                )
            },
            materials = state.materials.map {
                RelayMaterial(it.id, it.ownerId, it.title, it.kind.name, it.week, it.chapter)
            },
            records = state.events.takeLast(2_000).mapNotNull { event ->
                val session = sessions[event.sessionId] ?: return@mapNotNull null
                RelayRecord(event.id, session.ownerId, event.sessionId, event.timestamp, event.kind.name, event.content)
            },
        )
    }

    private fun request(method: String, url: String, token: String, body: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("中继返回 HTTP $code")
            return text
        } finally {
            connection.disconnect()
        }
    }
}

class RelaySyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CourseTraceApplication
        return RelaySyncService(app.repository, app.secureSettings).sync().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}

object RelaySyncScheduler {
    private const val WORK_NAME = "coursetrace-relay-sync"

    fun update(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RelaySyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
