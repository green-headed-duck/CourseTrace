package com.coursetrace.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.coursetrace.app.CourseTraceApplication
import com.coursetrace.app.model.ImportDraft
import com.coursetrace.app.model.ImportSource
import com.coursetrace.app.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/**
 * Developer-mode maintenance import. The manifest protects this receiver with
 * android.permission.DUMP, which ordinary third-party apps cannot hold.
 */
class AdbImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val application = context.applicationContext as CourseTraceApplication
                if (intent.action == ACTION_AUDIT) {
                    logAudit(application)
                    return@runCatching
                }
                require(intent.action == ACTION_IMPORT) { "Unsupported maintenance action" }
                val encoded = intent.getStringExtra(EXTRA_PAYLOAD_BASE64) ?: error("Missing payload")
                val raw = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                val profile = application.repository.state.value.preferences.scheduleTimeProfile
                val payload = TimetablePayloadParser.parse(raw, profile)
                val draft = ImportDraft(
                    createdAt = OffsetDateTime.now().toString(),
                    source = ImportSource.MANUAL,
                    sourceName = "USB 调试 · 已确认个人课表",
                    slots = payload.slots,
                    unscheduledCourses = payload.unscheduledCourses,
                    warnings = payload.warnings,
                    termName = payload.termName,
                    termStartDate = payload.termStartDate,
                    termWeekCount = payload.termWeekCount,
                )
                application.repository.saveImportDraft(draft)
                application.repository.commitImportDraft(draft.id)
                application.repository.clearImportDrafts()
                NotificationScheduler(application).reschedule(application.repository.state.value)
                Log.i(TAG, "Imported ${draft.slots.size} timed slots and ${draft.unscheduledCourses.size} unscheduled courses")
                logAudit(application)
            }.onFailure { error ->
                Log.e(TAG, "Import failed", error)
            }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_IMPORT = "com.coursetrace.app.action.ADB_IMPORT_CONFIRMED"
        const val ACTION_AUDIT = "com.coursetrace.app.action.ADB_AUDIT"
        const val EXTRA_PAYLOAD_BASE64 = "payload_base64"
        private const val TAG = "CourseTraceAdbImport"
    }

    private suspend fun logAudit(application: CourseTraceApplication) {
        val state = application.repository.state.value
        val term = state.activeTermId?.let { id -> state.terms.find { it.id == id } }
        val history = application.repository.gitHistory.recentHistory(3)
        Log.i(
            TAG,
            "AUDIT term=${term?.name}; start=${term?.startDate}; weeks=${term?.weekCount}; " +
                "courses=${state.courses.count { !it.archived }}; slots=${state.slots.size}; drafts=${state.importDrafts.size}; " +
                "git=${history.joinToString(" | ") { it.message }}",
        )
        state.courses.filterNot { it.archived }.forEach { course ->
            Log.i(TAG, "COURSE id=${course.id}; name=${course.name}; slots=${state.slots.count { it.courseId == course.id }}")
        }
    }
}
