package com.coursetrace.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.coursetrace.app.MainActivity
import com.coursetrace.app.R
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.domain.ScheduledClass
import com.coursetrace.app.model.AppState
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

class NotificationScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val schedulePreferences = context.getSharedPreferences("scheduled_alarms", Context.MODE_PRIVATE)

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    REMINDER_CHANNEL,
                    "上课提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "在课程开始前提醒" },
                NotificationChannel(
                    LIVE_CHANNEL,
                    "正在上课",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "在系统状态区显示即将开始或正在进行的课程"
                    setSound(null, null)
                },
            ),
        )
    }

    fun reschedule(state: AppState) {
        cancelTrackedAlarms()
        val requestCodes = mutableSetOf<String>()
        ScheduleEngine.upcoming(state, days = 60).forEach { scheduled ->
            val lead = scheduled.course.defaultReminderMinutes.takeIf { it > 0 }
                ?: state.preferences.notificationLeadMinutes
            val trigger = scheduled.start.minusMinutes(lead.toLong())
            if (trigger > LocalDateTime.now()) {
                schedule(trigger, scheduled).forEach { requestCodes += it.toString() }
            }
        }
        schedulePreferences.edit().putStringSet("request_codes", requestCodes).apply()
    }

    private fun schedule(trigger: LocalDateTime, scheduled: ScheduledClass): Set<Int> {
        val code = requestCode(scheduled)
        scheduleAlarm(trigger, scheduled, code, MODE_REMINDER)
        val liveCode = code xor LIVE_CODE_MASK
        val liveAt = scheduled.start.minusMinutes(15)
        if (liveAt > LocalDateTime.now() && liveAt != trigger) scheduleAlarm(liveAt, scheduled, liveCode, MODE_LIVE)
        val endCode = code xor END_CODE_MASK
        scheduleAlarm(scheduled.end.plusMinutes(1), scheduled, endCode, MODE_END)
        return setOf(code, liveCode, endCode)
    }

    private fun scheduleAlarm(at: LocalDateTime, scheduled: ScheduledClass, code: Int, mode: String) {
        val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
            putExtra(EXTRA_COURSE_ID, scheduled.course.id)
            putExtra(EXTRA_DATE, scheduled.date.toString())
            putExtra(EXTRA_MODE, mode)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val atMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun cancelTrackedAlarms() {
        schedulePreferences.getStringSet("request_codes", emptySet()).orEmpty().forEach { value ->
            val code = value.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, CourseAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) alarmManager.cancel(pending)
        }
    }

    fun showReminder(scheduled: ScheduledClass) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${scheduled.course.name} · 即将上课")
            .setContentText("${scheduled.start.toLocalTime()}  ${scheduled.room.ifBlank { "教室待确认" }}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append("${scheduled.start.toLocalTime()}–${scheduled.end.toLocalTime()}")
                        append("  ${scheduled.room.ifBlank { "教室待确认" }}")
                        if (scheduled.course.teacher.isNotBlank()) append("\n${scheduled.course.teacher}")
                        if (scheduled.note.isNotBlank()) append("\n${scheduled.note}")
                    },
                ),
            )
            .setContentIntent(contentIntent(scheduled))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(requestCode(scheduled), notification)
        } catch (_: SecurityException) {
            // The permission can be revoked between the check and this call.
        }

        val now = LocalDateTime.now()
        if (Build.VERSION.SDK_INT >= 36 && now >= scheduled.start.minusMinutes(15) && now <= scheduled.end) {
            showLiveClass(scheduled, now)
        }
    }

    @RequiresApi(36)
    fun showLiveClass(scheduled: ScheduledClass, now: LocalDateTime = LocalDateTime.now()) {
        if (!canNotify()) return
        val totalMinutes = Duration.between(scheduled.start, scheduled.end).toMinutes().coerceAtLeast(1)
        val elapsed = Duration.between(scheduled.start, now).toMinutes().coerceIn(0, totalMinutes)
        val progress = (elapsed.toDouble() / totalMinutes * 1000).roundToInt()
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progress)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
            .addProgressSegment(Notification.ProgressStyle.Segment(1000).setColor(scheduled.course.colorArgb.toInt()))
        val extras = Bundle().apply { putBoolean("android.requestPromotedOngoing", true) }
        val notification = Notification.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(scheduled.course.name)
            .setContentText("${scheduled.room.ifBlank { "教室待确认" }} · 至 ${scheduled.end.toLocalTime()}")
            .setSubText("课迹 · 正在上课")
            .setContentIntent(contentIntent(scheduled))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setStyle(style)
            .addExtras(extras)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(LIVE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission can be revoked between the check and this call.
        }
    }

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(scheduled: ScheduledClass): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("coursetrace://course/${scheduled.course.id}?date=${scheduled.date}"),
            context,
            MainActivity::class.java,
        )
        return PendingIntent.getActivity(
            context,
            requestCode(scheduled),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(scheduled: ScheduledClass): Int =
        (scheduled.course.id + scheduled.date.toString() + scheduled.start.toLocalTime()).hashCode()

    companion object {
        const val REMINDER_CHANNEL = "course_reminders"
        const val LIVE_CHANNEL = "live_class"
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_MODE = "mode"
        const val MODE_REMINDER = "reminder"
        const val MODE_LIVE = "live"
        const val MODE_END = "end"
        const val LIVE_NOTIFICATION_ID = 9001
        private const val LIVE_CODE_MASK = 0x1357
        private const val END_CODE_MASK = 0x2468
    }
}
