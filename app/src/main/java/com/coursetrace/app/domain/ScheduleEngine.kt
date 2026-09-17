package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
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

object ScheduleEngine {
    fun weekNumber(term: Term, date: LocalDate): Int {
        val start = LocalDate.parse(term.startDate)
        return (ChronoUnit.DAYS.between(start, date).floorDiv(7) + 1).toInt()
    }

    fun classesOn(state: AppState, date: LocalDate): List<ScheduledClass> {
        val term = state.activeTermId?.let { id -> state.terms.find { it.id == id } }
            ?: state.terms.firstOrNull { !it.archived }
            ?: return emptyList()
        val week = weekNumber(term, date)
        if (week !in 1..term.weekCount) return emptyList()

        val termCourseIds = state.courses.filter { it.termId == term.id && !it.archived }.associateBy { it.id }
        return state.slots.asSequence()
            .filter { it.courseId in termCourseIds }
            .filter { it.dayOfWeek == date.dayOfWeek.value }
            .filter { it.weeks.isEmpty() && week in it.startWeek..it.endWeek || week in it.weeks }
            .filter {
                if (it.weeks.isNotEmpty()) true else when (it.weekPattern) {
                    WeekPattern.EVERY -> true
                    WeekPattern.ODD -> week % 2 == 1
                    WeekPattern.EVEN -> week % 2 == 0
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
                    note = exception?.note.orEmpty(),
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
