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
import java.time.LocalDateTime
import java.time.ZoneId

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
                    "课程实时状态",
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
        context.getSystemService(NotificationManager::class.java).cancel(LIVE_NOTIFICATION_ID)
        val requestCodes = mutableSetOf<String>()
        val now = LocalDateTime.now()
        ScheduleEngine.upcoming(state, days = 60).forEach { scheduled ->
            val lead = ClassNotificationPresenter.reminderLeadMinutes(scheduled.course, state.preferences)
            if (lead <= 0) return@forEach
            val trigger = scheduled.start.minusMinutes(lead.toLong())
            schedule(trigger, scheduled, now).forEach { requestCodes += it.toString() }
            if (trigger <= now && now < scheduled.end) {
                if (now < scheduled.start || Build.VERSION.SDK_INT < 36) {
                    showReminder(scheduled, now)
                } else {
                    showLiveClass(scheduled, now)
                }
            }
        }
        schedulePreferences.edit().putStringSet("request_codes", requestCodes).apply()
    }

    private fun schedule(
        trigger: LocalDateTime,
        scheduled: ScheduledClass,
        now: LocalDateTime,
    ): Set<Int> {
        val codes = mutableSetOf<Int>()
        val code = requestCode(scheduled)
        if (trigger > now) {
            scheduleAlarm(trigger, scheduled, code, MODE_REMINDER)
            codes += code
        }
        val startCode = code xor START_CODE_MASK
        if (scheduled.start > now) {
            scheduleAlarm(scheduled.start, scheduled, startCode, MODE_START)
            codes += startCode
        }
        val endCode = code xor END_CODE_MASK
        if (scheduled.end > now) {
            scheduleAlarm(scheduled.end, scheduled, endCode, MODE_END)
            codes += endCode
        }
        return codes
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

    fun showReminder(scheduled: ScheduledClass, now: LocalDateTime = LocalDateTime.now()) {
        if (!canNotify()) return
        val presentation = ClassNotificationPresenter.present(scheduled, now)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(presentation.title)
            .setContentText(presentation.text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(presentation.text)
                        if (scheduled.course.teacher.isNotBlank()) append("\n${scheduled.course.teacher}")
                        if (scheduled.note.isNotBlank()) append("\n${scheduled.note}")
                    },
                ),
            )
            .setContentIntent(contentIntent(scheduled))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(requestCode(scheduled), notification)
        } catch (_: SecurityException) {
            // The permission can be revoked between the check and this call.
        }

        if (Build.VERSION.SDK_INT >= 36 && now <= scheduled.end) {
            showLiveClass(scheduled, now)
        }
    }

    @RequiresApi(36)
    fun showLiveClass(scheduled: ScheduledClass, now: LocalDateTime = LocalDateTime.now()) {
        if (!canNotify()) return
        val presentation = ClassNotificationPresenter.present(scheduled, now)
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(presentation.progress)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
            .addProgressSegment(Notification.ProgressStyle.Segment(1000).setColor(scheduled.course.colorArgb.toInt()))
        val extras = Bundle().apply { putBoolean("android.requestPromotedOngoing", true) }
        val notification = Notification.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(presentation.title)
            .setContentText(presentation.text)
            .setSubText(presentation.subText)
            .setContentIntent(contentIntent(scheduled))
            .setWhen(scheduled.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(presentation.upcoming)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(
                java.time.Duration.between(now, scheduled.end).toMillis().coerceAtLeast(1_000),
            )
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
        const val MODE_START = "start"
        const val MODE_LIVE = "live" // 兼容旧版本已经登记的闹钟。
        const val MODE_END = "end"
        const val LIVE_NOTIFICATION_ID = 9001
        private const val START_CODE_MASK = 0x1357
        private const val END_CODE_MASK = 0x2468
    }
}
