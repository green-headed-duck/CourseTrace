package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import com.coursetrace.app.model.CalendarDayRule
import com.coursetrace.app.model.CalendarRuleType
import com.coursetrace.app.model.AppPreferences
import com.coursetrace.app.model.DEFAULT_HOLIDAY_FEED_URL
import com.coursetrace.app.model.Term
import com.coursetrace.app.model.WeekPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleEngineTest {
    @Test
    fun oddWeekCourseOnlyAppearsOnOddWeeks() {
        val term = Term(name = "测试学期", startDate = "2026-09-07", weekCount = 20)
        val course = Course(termId = term.id, name = "数字电路")
        val slot = CourseSlot(
            courseId = course.id,
            dayOfWeek = 1,
            startTime = "08:50",
            endTime = "10:25",
            startWeek = 1,
            endWeek = 16,
            weekPattern = WeekPattern.ODD,
        )
        val state = AppState(
            terms = listOf(term),
            courses = listOf(course),
            slots = listOf(slot),
            activeTermId = term.id,
        )

        assertEquals(1, ScheduleEngine.classesOn(state, LocalDate.parse("2026-09-07")).size)
        assertTrue(ScheduleEngine.classesOn(state, LocalDate.parse("2026-09-14")).isEmpty())
        assertEquals(1, ScheduleEngine.classesOn(state, LocalDate.parse("2026-09-21")).size)
    }

    @Test
    fun archivedCourseIsHiddenWithoutDeletingItsSlot() {
        val term = Term(name = "测试学期", startDate = "2026-09-07", weekCount = 20)
        val course = Course(termId = term.id, name = "FPGA", archived = true)
        val slot = CourseSlot(
            courseId = course.id,
            dayOfWeek = 1,
            startTime = "08:50",
            endTime = "10:25",
        )
        val state = AppState(
            terms = listOf(term),
            courses = listOf(course),
            slots = listOf(slot),
            activeTermId = term.id,
        )

        assertTrue(ScheduleEngine.classesOn(state, LocalDate.parse("2026-09-07")).isEmpty())
        assertEquals(1, state.slots.size)
    }

    @Test
    fun explicitWeeksPreserveGaps() {
        val term = Term(name = "测试学期", startDate = "2026-08-31", weekCount = 22)
        val course = Course(termId = term.id, name = "微积分")
        val slot = CourseSlot(
            courseId = course.id,
            dayOfWeek = 1,
            startTime = "08:50",
            endTime = "10:25",
            startWeek = 3,
            endWeek = 18,
            weeks = (3..12).toList() + (15..18).toList(),
        )
        val state = AppState(terms = listOf(term), courses = listOf(course), slots = listOf(slot), activeTermId = term.id)

        assertEquals(1, ScheduleEngine.classesOn(state, LocalDate.parse("2026-09-14")).size)
        assertTrue(ScheduleEngine.classesOn(state, LocalDate.parse("2026-11-30")).isEmpty())
        assertEquals(1, ScheduleEngine.classesOn(state, LocalDate.parse("2026-12-07")).size)
    }

    @Test
    fun holidayRuleAutomaticallyRemovesClasses() {
        val term = Term(name = "测试学期", startDate = "2026-08-31", weekCount = 22)
        val course = Course(termId = term.id, name = "数字电路")
        val slot = CourseSlot(courseId = course.id, dayOfWeek = 4, startTime = "08:50", endTime = "10:25")
        val rule = CalendarDayRule(
            id = "national-day",
            date = "2026-10-01",
            type = CalendarRuleType.NO_CLASS,
            title = "国庆节",
            sourceUrl = DEFAULT_HOLIDAY_FEED_URL,
            sourceName = "国务院办公厅",
        )
        val state = AppState(
            terms = listOf(term),
            courses = listOf(course),
            slots = listOf(slot),
            calendarDayRules = listOf(rule),
            activeTermId = term.id,
        )

        assertTrue(ScheduleEngine.classesOn(state, LocalDate.parse("2026-10-01")).isEmpty())
    }

    @Test
    fun followDateRuleCopiesSpecifiedScheduleToTargetDate() {
        val term = Term(name = "测试学期", startDate = "2026-08-31", weekCount = 22)
        val course = Course(termId = term.id, name = "数字电路")
        val slot = CourseSlot(courseId = course.id, dayOfWeek = 1, startTime = "08:50", endTime = "10:25")
        val rule = CalendarDayRule(
            id = "make-up",
            date = "2026-09-20",
            type = CalendarRuleType.FOLLOW_DATE,
            title = "按周一课表补课",
            sourceDate = "2026-09-14",
            sourceUrl = DEFAULT_HOLIDAY_FEED_URL,
            sourceName = "测试源",
        )
        val state = AppState(
            terms = listOf(term),
            courses = listOf(course),
            slots = listOf(slot),
            calendarDayRules = listOf(rule),
            activeTermId = term.id,
        )

        val classes = ScheduleEngine.classesOn(state, LocalDate.parse("2026-09-20"))
        assertEquals(1, classes.size)
        assertEquals(LocalDate.parse("2026-09-20"), classes.single().date)
        assertTrue(classes.single().note.contains("补课"))
    }

    @Test
    fun disabledHolidaySyncLeavesOriginalScheduleUntouched() {
        val term = Term(name = "测试学期", startDate = "2026-08-31", weekCount = 22)
        val course = Course(termId = term.id, name = "数字电路")
        val slot = CourseSlot(courseId = course.id, dayOfWeek = 4, startTime = "08:50", endTime = "10:25")
        val rule = CalendarDayRule(
            id = "national-day",
            date = "2026-10-01",
            type = CalendarRuleType.NO_CLASS,
            title = "国庆节",
            sourceUrl = DEFAULT_HOLIDAY_FEED_URL,
            sourceName = "国务院办公厅",
        )
        val state = AppState(
            terms = listOf(term),
            courses = listOf(course),
            slots = listOf(slot),
            calendarDayRules = listOf(rule),
            preferences = AppPreferences(holidaySync = AppPreferences().holidaySync.copy(enabled = false)),
            activeTermId = term.id,
        )

        assertEquals(1, ScheduleEngine.classesOn(state, LocalDate.parse("2026-10-01")).size)
    }
}
