package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
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
}
