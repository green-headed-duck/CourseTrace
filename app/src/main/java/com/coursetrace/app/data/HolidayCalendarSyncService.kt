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
import com.coursetrace.app.model.CalendarDayRule
import com.coursetrace.app.model.CalendarRuleType
import com.coursetrace.app.notifications.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Serializable
data class HolidayCalendarFeed(
    val schemaVersion: Int,
    val sourceName: String,
    val sourcePage: String,
    val updatedAt: String,
    val rules: List<HolidayRuleDto>,
)

@Serializable
data class HolidayRuleDto(
    val id: String,
    val date: String,
    val type: CalendarRuleType,
    val title: String,
    val sourceDate: String? = null,
)

object HolidayCalendarFeedParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, sourceUrl: String): Pair<HolidayCalendarFeed, List<CalendarDayRule>> {
        requireHttps(sourceUrl)
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_FEED_BYTES) { "节假日数据超过 1 MB" }
        val feed = json.decodeFromString<HolidayCalendarFeed>(raw)
        require(feed.schemaVersion == 1) { "不支持的节假日数据版本" }
        require(feed.sourceName.isNotBlank() && feed.sourceName.length <= 80) { "数据源名称无效" }
        requireHttps(feed.sourcePage)
        LocalDate.parse(feed.updatedAt)
        require(feed.rules.size <= 500) { "节假日规则数量超过限制" }
        require(feed.rules.map { it.id }.distinct().size == feed.rules.size) { "规则 ID 重复" }
        require(feed.rules.map { it.date }.distinct().size == feed.rules.size) { "同一天存在多条规则" }
        val rules = feed.rules.map { dto ->
            require(dto.id.matches(Regex("[A-Za-z0-9._:-]{1,100}"))) { "规则 ID 无效" }
            LocalDate.parse(dto.date)
            require(dto.title.isNotBlank() && dto.title.length <= 80) { "规则标题无效" }
            val sourceDate = dto.sourceDate?.also(LocalDate::parse)
            if (dto.type == CalendarRuleType.FOLLOW_DATE) {
                require(sourceDate != null && sourceDate != dto.date) { "补课规则必须提供不同的 sourceDate" }
            } else {
                require(sourceDate == null) { "只有 FOLLOW_DATE 可以提供 sourceDate" }
            }
            CalendarDayRule(
                id = dto.id,
                date = dto.date,
                type = dto.type,
                title = dto.title,
                sourceDate = sourceDate,
                sourceUrl = sourceUrl,
                sourceName = feed.sourceName,
                sourcePage = feed.sourcePage,
                sourceUpdatedAt = feed.updatedAt,
            )
        }
        return feed to rules.sortedBy { it.date }
    }

    fun requireHttps(url: String) {
        require(url.length in 1..2048) { "数据源地址无效" }
        val uri = runCatching { URI(url) }.getOrElse { error("数据源地址无效") }
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        ) {
            "节假日数据源必须使用 HTTPS"
        }
    }

    private const val MAX_FEED_BYTES = 1024 * 1024
}

class HolidayCalendarSyncService(
    private val context: Context,
    private val repository: AppRepository,
) {
    suspend fun sync(force: Boolean = true): Result<Int> = withContext(Dispatchers.IO) {
        SYNC_MUTEX.withLock {
            runCatching {
                val state = repository.state.value
                val profile = state.preferences.holidaySync
                require(profile.enabled) { "节假日自动同步尚未启用" }
                HolidayCalendarFeedParser.requireHttps(profile.sourceUrl)
                if (!force && !isSyncDue(profile.sourceUrl)) {
                    return@runCatching state.calendarDayRules.count { it.sourceUrl == profile.sourceUrl }
                }
                val raw = request(profile.sourceUrl)
                val (feed, rules) = HolidayCalendarFeedParser.parse(raw, profile.sourceUrl)
                val nextProfile = profile.copy(
                    sourceName = feed.sourceName,
                    sourcePage = feed.sourcePage,
                    sourceUpdatedAt = feed.updatedAt,
                    lastSyncAt = OffsetDateTime.now().toString(),
                )
                val currentState = repository.state.value
                require(currentState.preferences.holidaySync.enabled && currentState.preferences.holidaySync.sourceUrl == profile.sourceUrl) {
                    "节假日数据源已更改，请重新同步"
                }
                val currentProfile = currentState.preferences.holidaySync
                val contentChanged = currentState.calendarDayRules != rules ||
                    currentProfile.sourceName != feed.sourceName ||
                    currentProfile.sourcePage != feed.sourcePage ||
                    currentProfile.sourceUpdatedAt != feed.updatedAt
                if (contentChanged) {
                    repository.replaceCalendarDayRules(rules, nextProfile)
                    NotificationScheduler(context).reschedule(repository.state.value)
                }
                rememberSuccessfulCheck(profile.sourceUrl)
                rules.size
            }
        }
    }

    private fun isSyncDue(sourceUrl: String): Boolean {
        val preferences = context.getSharedPreferences(SYNC_PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(KEY_SOURCE_URL, null) != sourceUrl) return true
        val lastCheckMillis = preferences.getLong(KEY_LAST_CHECK_MILLIS, 0L)
        return System.currentTimeMillis() - lastCheckMillis >= MIN_BACKGROUND_CHECK_INTERVAL_MILLIS
    }

    private fun rememberSuccessfulCheck(sourceUrl: String) {
        context.getSharedPreferences(SYNC_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE_URL, sourceUrl)
            .putLong(KEY_LAST_CHECK_MILLIS, System.currentTimeMillis())
            .apply()
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CourseTrace-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("数据源返回 HTTP $code")
            require(connection.url.protocol.equals("https", ignoreCase = true)) { "数据源重定向到了非 HTTPS 地址" }
            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0 || declaredLength <= MAX_FEED_BYTES) { "节假日数据超过 1 MB" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    require(output.size() + read <= MAX_FEED_BYTES) { "节假日数据超过 1 MB" }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_FEED_BYTES = 1024 * 1024
        const val SYNC_PREFERENCES = "holiday_calendar_sync"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_LAST_CHECK_MILLIS = "last_check_millis"
        const val MIN_BACKGROUND_CHECK_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L
        val SYNC_MUTEX = Mutex()
    }
}

class HolidayCalendarSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CourseTraceApplication
        if (!app.repository.state.value.preferences.holidaySync.enabled) return Result.success()
        return HolidayCalendarSyncService(app, app.repository).sync(force = false).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}

object HolidayCalendarSyncScheduler {
    private const val WORK_NAME = "coursetrace-holiday-calendar-sync"

    fun update(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<HolidayCalendarSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
