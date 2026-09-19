package com.coursetrace.app.bridge

import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.model.AppState
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

data class BusyEpochWindow(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)

object StudyBridgeContract {
    const val AUTHORITY = "com.coursetrace.app.studybridge"
    const val PATH_BUSY_WINDOWS = "v1/busy_windows"
    const val COLUMN_START_EPOCH = "start_epoch_millis"
    const val COLUMN_END_EPOCH = "end_epoch_millis"
    val COLUMNS: List<String> = listOf(COLUMN_START_EPOCH, COLUMN_END_EPOCH)
    const val BUSY_WINDOWS_URI_STRING = "content://$AUTHORITY/$PATH_BUSY_WINDOWS"

    fun parseDate(value: String?): LocalDate {
        if (value.isNullOrBlank()) throw IllegalArgumentException("date query parameter is required")
        val date = try {
            LocalDate.parse(value)
        } catch (error: DateTimeException) {
            throw IllegalArgumentException("date must use YYYY-MM-DD", error)
        }
        if (date.toString() != value) throw IllegalArgumentException("date must use YYYY-MM-DD")
        return date
    }

    fun validateProjection(projection: Array<out String>?): List<String> {
        val selected = projection?.toList() ?: COLUMNS
        if (selected.isEmpty() || selected.any { it !in COLUMNS }) {
            throw IllegalArgumentException("Only busy-window time columns are available")
        }
        return selected.distinct()
    }

    fun busyWindows(
        state: AppState,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<BusyEpochWindow> {
        val raw = ScheduleEngine.classesOn(state, date)
            .map { scheduledClass ->
                BusyEpochWindow(
                    startEpochMillis = scheduledClass.start.atZone(zoneId).toInstant().toEpochMilli(),
                    endEpochMillis = scheduledClass.end.atZone(zoneId).toInstant().toEpochMilli(),
                )
            }
        return mergeOverlapping(raw)
    }

    internal fun mergeOverlapping(windows: List<BusyEpochWindow>): List<BusyEpochWindow> {
        val sorted = windows.filter { it.endEpochMillis > it.startEpochMillis }
            .sortedBy(BusyEpochWindow::startEpochMillis)
        if (sorted.isEmpty()) return emptyList()

        val merged = mutableListOf(sorted.first())
        sorted.drop(1).forEach { next ->
            val current = merged.last()
            if (next.startEpochMillis < current.endEpochMillis) {
                merged[merged.lastIndex] = current.copy(
                    endEpochMillis = maxOf(current.endEpochMillis, next.endEpochMillis),
                )
            } else {
                merged += next
            }
        }
        return merged
    }
}

object StudyBridgeAccessPolicy {
    const val ALLOWED_PACKAGE = "com.cettrace.app"
    const val ALLOWED_CERTIFICATE_SHA256 =
        "d1e9182ebc2f2628097e9ee18e0669442dc0af4166a0a65d3c5f7aabb0c0ef7a"

    fun isCallerAllowed(packagesForUid: Collection<String>?, certificateDigests: Collection<String>): Boolean =
        packagesForUid?.toSet() == setOf(ALLOWED_PACKAGE) &&
            certificateDigests.any { it.lowercase() == ALLOWED_CERTIFICATE_SHA256 }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
