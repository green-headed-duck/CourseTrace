package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import com.coursetrace.app.model.CalendarDayRule
import com.coursetrace.app.model.CalendarRuleType
import com.coursetrace.app.model.Term
import com.coursetrace.app.model.WeekPattern
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class ScheduledClass(
    val course: Course,
    val slot: CourseSlot,
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val room: String,
    val note: String = "",
)

data class ResolvedCalendarDayRule(
    val baseRule: CalendarDayRule,
    val type: CalendarRuleType,
    val sourceDate: String?,
    val isUserOverride: Boolean,
) {
    val date: String get() = baseRule.date
    val title: String get() = baseRule.title
    val sourceName: String get() = baseRule.sourceName
}

object ScheduleEngine {
    fun weekNumber(term: Term, date: LocalDate): Int {
        val start = LocalDate.parse(term.startDate)
        return (ChronoUnit.DAYS.between(start, date).floorDiv(7) + 1).toInt()
    }

    fun classesOn(state: AppState, date: LocalDate): List<ScheduledClass> {
        val dayRule = dayRule(state, date)
        if (dayRule?.type == CalendarRuleType.NO_CLASS) return emptyList()
        val scheduleDate = if (dayRule?.type == CalendarRuleType.FOLLOW_DATE) {
            dayRule.sourceDate?.let(LocalDate::parse) ?: date
        } else {
            date
        }
        val term = state.activeTermId?.let { id -> state.terms.find { it.id == id } }
            ?: state.terms.firstOrNull { !it.archived }
            ?: return emptyList()
        val occurrenceWeek = weekNumber(term, date)
        if (occurrenceWeek !in 1..term.weekCount) return emptyList()
        val scheduleWeek = weekNumber(term, scheduleDate)
        if (scheduleWeek !in 1..term.weekCount) return emptyList()

        val termCourseIds = state.courses.filter { it.termId == term.id && !it.archived }.associateBy { it.id }
        return state.slots.asSequence()
            .filter { it.courseId in termCourseIds }
            .filter { it.dayOfWeek == scheduleDate.dayOfWeek.value }
            .filter { it.weeks.isEmpty() && scheduleWeek in it.startWeek..it.endWeek || scheduleWeek in it.weeks }
            .filter {
                if (it.weeks.isNotEmpty()) true else when (it.weekPattern) {
                    WeekPattern.EVERY -> true
                    WeekPattern.ODD -> scheduleWeek % 2 == 1
                    WeekPattern.EVEN -> scheduleWeek % 2 == 0
                }
            }
            .mapNotNull { slot ->
                val course = termCourseIds.getValue(slot.courseId)
                val exception = state.exceptions.find {
                    it.courseId == course.id && it.originalDate == date.toString()
                }
                if (exception?.cancelled == true) return@mapNotNull null
                if (exception?.replacementDate != null && exception.replacementDate != date.toString()) {
                    return@mapNotNull null
                }
                val startTime = LocalTime.parse(exception?.replacementStartTime ?: slot.startTime)
                val endTime = LocalTime.parse(exception?.replacementEndTime ?: slot.endTime)
                ScheduledClass(
                    course = course,
                    slot = slot,
                    date = date,
                    start = date.atTime(startTime),
                    end = date.atTime(endTime),
                    room = exception?.replacementRoom ?: slot.room,
                    note = listOfNotNull(
                        dayRule?.takeIf { it.type == CalendarRuleType.FOLLOW_DATE }?.let {
                            "${it.title} · 按 ${it.sourceDate} 课表"
                        },
                        exception?.note?.takeIf(String::isNotBlank),
                    ).joinToString(" · "),
                )
            }
            .sortedBy { it.start }
            .toList()
    }

    fun upcoming(
        state: AppState,
        from: LocalDateTime = LocalDateTime.now(),
        days: Long = 21,
    ): List<ScheduledClass> = (0..days)
        .asSequence()
        .flatMap { classesOn(state, from.toLocalDate().plusDays(it)).asSequence() }
        .filter { it.end >= from }
        .sortedBy { it.start }
        .toList()

    fun currentOrNext(state: AppState, now: LocalDateTime = LocalDateTime.now()): ScheduledClass? =
        upcoming(state, now).firstOrNull()

    fun dayRule(state: AppState, date: LocalDate): ResolvedCalendarDayRule? {
        val profile = state.preferences.holidaySync
        if (!profile.enabled) return null
        val baseRule = state.calendarDayRules.find { it.date == date.toString() && it.sourceUrl == profile.sourceUrl }
            ?: return null
        val override = state.calendarDayOverrides.find { it.date == date.toString() }
            ?.takeIf { baseRule.type != CalendarRuleType.NO_CLASS }
        return ResolvedCalendarDayRule(
            baseRule = baseRule,
            type = if (override != null) CalendarRuleType.FOLLOW_DATE else baseRule.type,
            sourceDate = override?.sourceDate ?: baseRule.sourceDate,
            isUserOverride = override != null,
        )
    }

    fun nextEarlyClass(
        state: AppState,
        threshold: LocalTime,
        fromDate: LocalDate = LocalDate.now(),
    ): ScheduledClass? = (0L..14L)
        .asSequence()
        .flatMap { classesOn(state, fromDate.plusDays(it)).asSequence() }
        .firstOrNull { it.start.toLocalTime() < threshold }

    fun dayLabel(day: Int): String = DayOfWeek.of(day).let {
        when (it) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    }
}
