package com.coursetrace.app.notifications

import com.coursetrace.app.domain.ScheduledClass
import com.coursetrace.app.model.AppPreferences
import com.coursetrace.app.model.Course
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class ClassNotificationPresentation(
    val upcoming: Boolean,
    val title: String,
    val text: String,
    val subText: String,
    val progress: Int,
)

internal object ClassNotificationPresenter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun reminderLeadMinutes(course: Course, preferences: AppPreferences): Int =
        course.reminderOverrideMinutes ?: preferences.notificationLeadMinutes

    fun present(scheduled: ScheduledClass, now: LocalDateTime): ClassNotificationPresentation {
        val start = scheduled.start.format(timeFormatter)
        val end = scheduled.end.format(timeFormatter)
        val room = scheduled.room.ifBlank { "教室待确认" }
        val upcoming = now < scheduled.start
        if (upcoming) {
            val seconds = Duration.between(now, scheduled.start).seconds.coerceAtLeast(0)
            val minutes = (seconds + 59) / 60
            val remaining = if (minutes > 0) "还有 $minutes 分钟" else "即将开始"
            return ClassNotificationPresentation(
                upcoming = true,
                title = "${scheduled.course.name} · ${start}上课",
                text = "$start–$end · $room · $remaining",
                subText = "课迹 · 即将上课",
                progress = 0,
            )
        }

        val totalMinutes = Duration.between(scheduled.start, scheduled.end).toMinutes().coerceAtLeast(1)
        val elapsed = Duration.between(scheduled.start, now).toMinutes().coerceIn(0, totalMinutes)
        return ClassNotificationPresentation(
            upcoming = false,
            title = "${scheduled.course.name} · 上课中",
            text = "$start 开始 · $end 下课 · $room",
            subText = "课迹 · 上课中",
            progress = (elapsed.toDouble() / totalMinutes * 1000).toInt().coerceIn(0, 1000),
        )
    }
}
