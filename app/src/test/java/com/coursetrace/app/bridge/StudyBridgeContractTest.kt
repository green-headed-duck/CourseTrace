package com.coursetrace.app.bridge

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import com.coursetrace.app.model.ScheduleException
import com.coursetrace.app.model.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StudyBridgeContractTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesOnlyCanonicalIsoDates() {
        assertEquals(LocalDate.of(2026, 9, 21), StudyBridgeContract.parseDate("2026-09-21"))
        assertThrows(IllegalArgumentException::class.java) { StudyBridgeContract.parseDate(null) }
        assertThrows(IllegalArgumentException::class.java) { StudyBridgeContract.parseDate("2026-9-21") }
        assertThrows(IllegalArgumentException::class.java) { StudyBridgeContract.parseDate("not-a-date") }
    }

    @Test
    fun returnsNoWindowsWhenThereAreNoClasses() {
        assertTrue(StudyBridgeContract.busyWindows(AppState(), LocalDate.of(2026, 9, 21), zone).isEmpty())
    }

    @Test
    fun returnsOnlyStartAndEndForAnEffectiveClass() {
        val state = stateWithSlots(
            CourseSlot(
                courseId = COURSE_ID,
                dayOfWeek = 1,
                startTime = "08:50",
                endTime = "10:25",
            ),
        )

        val windows = StudyBridgeContract.busyWindows(state, LocalDate.of(2026, 9, 21), zone)

        assertEquals(
            listOf(
                BusyEpochWindow(epoch("2026-09-21T08:50"), epoch("2026-09-21T10:25")),
            ),
            windows,
        )
        assertEquals(
            listOf("start_epoch_millis", "end_epoch_millis"),
            StudyBridgeContract.COLUMNS,
        )
        assertThrows(IllegalArgumentException::class.java) {
            StudyBridgeContract.validateProjection(arrayOf("course_name"))
        }
    }

    @Test
    fun mergesOverlapsButPreservesRealBreaks() {
        val state = stateWithSlots(
            CourseSlot(courseId = COURSE_ID, dayOfWeek = 1, startTime = "08:50", endTime = "10:25"),
            CourseSlot(courseId = COURSE_ID, dayOfWeek = 1, startTime = "10:00", endTime = "11:25"),
            CourseSlot(courseId = COURSE_ID, dayOfWeek = 1, startTime = "11:30", endTime = "12:15"),
        )

        assertEquals(
            listOf(
                BusyEpochWindow(epoch("2026-09-21T08:50"), epoch("2026-09-21T11:25")),
                BusyEpochWindow(epoch("2026-09-21T11:30"), epoch("2026-09-21T12:15")),
            ),
            StudyBridgeContract.busyWindows(state, LocalDate.of(2026, 9, 21), zone),
        )
    }

    @Test
    fun cancelledClassIsNotExposed() {
        val original = stateWithSlots(
            CourseSlot(courseId = COURSE_ID, dayOfWeek = 1, startTime = "08:50", endTime = "10:25"),
        )
        val state = original.copy(
            exceptions = listOf(
                ScheduleException(
                    courseId = COURSE_ID,
                    originalDate = "2026-09-21",
                    cancelled = true,
                ),
            ),
        )

        assertTrue(StudyBridgeContract.busyWindows(state, LocalDate.of(2026, 9, 21), zone).isEmpty())
    }

    @Test
    fun callerRequiresExactPackageAndCertificate() {
        val allowed = StudyBridgeAccessPolicy.ALLOWED_CERTIFICATE_SHA256
        assertTrue(StudyBridgeAccessPolicy.isCallerAllowed(listOf("com.cettrace.app"), listOf(allowed)))
        assertFalse(StudyBridgeAccessPolicy.isCallerAllowed(listOf("com.example.copy"), listOf(allowed)))
        assertFalse(StudyBridgeAccessPolicy.isCallerAllowed(listOf("com.cettrace.app"), listOf("00")))
        assertFalse(
            StudyBridgeAccessPolicy.isCallerAllowed(
                listOf("com.cettrace.app", "com.example.shareduid"),
                listOf(allowed),
            ),
        )
    }

    @Test
    fun certificateDigestUsesLowercaseSha256() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            StudyBridgeAccessPolicy.sha256("abc".encodeToByteArray()),
        )
    }

    private fun stateWithSlots(vararg slots: CourseSlot): AppState {
        val term = Term(id = TERM_ID, name = "测试学期", startDate = "2026-08-31", weekCount = 22)
        val course = Course(id = COURSE_ID, termId = TERM_ID, name = "测试课程")
        return AppState(
            terms = listOf(term),
            courses = listOf(course),
            slots = slots.toList(),
            activeTermId = TERM_ID,
        )
    }

    private fun epoch(localDateTime: String): Long =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()

    companion object {
        private const val TERM_ID = "term"
        private const val COURSE_ID = "course"
    }
}
