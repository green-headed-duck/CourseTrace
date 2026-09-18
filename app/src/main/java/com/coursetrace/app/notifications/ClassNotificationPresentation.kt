package com.coursetrace.app.notifications

import com.coursetrace.app.domain.ScheduledClass
import com.coursetrace.app.model.AppPreferences
import com.coursetrace.app.model.Course
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class ClassNotificationPresentation(
    val upcoming: Boolean,
    val title: String,
    val compactTitle: String,
    val criticalText: String,
    val text: String,
    val subText: String,
)

internal object ClassNotificationPresenter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun reminderLeadMinutes(course: Course, preferences: AppPreferences): Int =
        course.reminderOverrideMinutes ?: preferences.notificationLeadMinutes

    fun liveLabel(course: Course): String {
        course.liveDisplayName.trim().takeIf { it.isNotEmpty() }?.let { return it.take(12) }
        val name = course.name.trim()
        val known = listOf(
            "C++" to "C++",
            "学术英语" to "学术英语",
            "集成电路与系统导论" to "集成导论",
            "习近平新时代" to "习思想",
            "高等数学" to "高等数学",
            "线性代数" to "线性代数",
            "现代商务礼仪" to "商务礼仪",
        ).firstOrNull { (keyword, _) -> name.contains(keyword, ignoreCase = true) }
        if (known != null) return known.second
        return name.substringBefore('（').substringBefore('(').substringBefore('：').trim().take(6)
            .ifBlank { "课程" }
    }

    fun present(scheduled: ScheduledClass, now: LocalDateTime): ClassNotificationPresentation {
        val start = scheduled.start.format(timeFormatter)
        val end = scheduled.end.format(timeFormatter)
        val room = scheduled.room.ifBlank { "教室待确认" }
        val label = liveLabel(scheduled.course)
        val compactTitle = "$label · $start"
        val upcoming = now < scheduled.start
        if (upcoming) {
            return ClassNotificationPresentation(
                upcoming = true,
                title = "${scheduled.course.name} · ${start}上课",
                compactTitle = compactTitle,
                criticalText = label,
                text = "$room · ${start}上课 · ${end}下课",
                subText = "即将上课",
            )
        }

        return ClassNotificationPresentation(
            upcoming = false,
            title = "${scheduled.course.name} · 上课中",
            compactTitle = compactTitle,
            criticalText = label,
            text = "$room · ${start}开始 · ${end}下课",
            subText = "上课中",
        )
    }
}
