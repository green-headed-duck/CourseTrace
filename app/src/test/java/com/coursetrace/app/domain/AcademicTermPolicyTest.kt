package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.Term
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AcademicTermPolicyTest {
    @Test
    fun september18IsThirdTeachingWeek() {
        val term = AcademicTermPolicy.defaultTerm(LocalDate.of(2026, 9, 18))

        assertEquals("2026-08-31", term.startDate)
        assertEquals(3, ScheduleEngine.weekNumber(term, LocalDate.of(2026, 9, 18)))
    }

    @Test
    fun repairsLegacyInstallWeekAnchorWithoutLosingCourses() {
        val oldTerm = Term(name = "2026-2027学年第1学期", startDate = "2026-09-14", weekCount = 20)
        val course = Course(termId = oldTerm.id, name = "数字电路")
        val original = AppState(
            terms = listOf(oldTerm),
            courses = listOf(course),
            activeTermId = oldTerm.id,
        )

        val repaired = AcademicTermPolicy.repairKnown2026Term(original)

        assertEquals("2026-08-31", repaired.terms.single().startDate)
        assertEquals(22, repaired.terms.single().weekCount)
        assertEquals(course, repaired.courses.single())
    }

    @Test
    fun calibrationComputesMondayFromTeachingWeek() {
        assertEquals(
            LocalDate.of(2026, 8, 31),
            AcademicTermPolicy.startDateForCurrentWeek(LocalDate.of(2026, 9, 18), 3),
        )
    }
}
