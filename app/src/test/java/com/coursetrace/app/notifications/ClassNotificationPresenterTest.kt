package com.coursetrace.app.notifications

import com.coursetrace.app.domain.ScheduledClass
import com.coursetrace.app.model.AppPreferences
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassNotificationPresenterTest {
    private val course = Course(termId = "term", name = "数字电路", teacher = "张老师")
    private val slot = CourseSlot(
        courseId = course.id,
        dayOfWeek = 1,
        startTime = "14:00",
        endTime = "15:35",
        room = "F3b-212",
    )
    private val scheduled = ScheduledClass(
        course = course,
        slot = slot,
        date = LocalDate.of(2026, 9, 21),
        start = LocalDateTime.of(2026, 9, 21, 14, 0),
        end = LocalDateTime.of(2026, 9, 21, 15, 35),
        room = slot.room,
    )

    @Test
    fun `global reminder lead is used when course has no override`() {
        assertEquals(
            30,
            ClassNotificationPresenter.reminderLeadMinutes(
                course,
                AppPreferences(notificationLeadMinutes = 30),
            ),
        )
    }

    @Test
    fun `course reminder override wins including disabled value`() {
        val preferences = AppPreferences(notificationLeadMinutes = 30)
        assertEquals(
            20,
            ClassNotificationPresenter.reminderLeadMinutes(
                course.copy(reminderOverrideMinutes = 20),
                preferences,
            ),
        )
        assertEquals(
            0,
            ClassNotificationPresenter.reminderLeadMinutes(
                course.copy(reminderOverrideMinutes = 0),
                preferences,
            ),
        )
    }

    @Test
    fun `before class leaves countdown to chronometer and prioritizes room and exact times`() {
        val presentation = ClassNotificationPresenter.present(
            scheduled,
            LocalDateTime.of(2026, 9, 21, 13, 30),
        )

        assertTrue(presentation.upcoming)
        assertEquals("数字电路 · 14:00上课", presentation.title)
        assertEquals("数字电路 · 14:00", presentation.compactTitle)
        assertEquals("数字电路", presentation.criticalText)
        assertEquals("F3b-212 · 14:00上课 · 15:35下课", presentation.text)
        assertFalse(presentation.text.contains("分钟"))
        assertEquals("即将上课", presentation.subText)
    }

    @Test
    fun `at class start switches to ongoing and keeps concrete times`() {
        val presentation = ClassNotificationPresenter.present(scheduled, scheduled.start)

        assertFalse(presentation.upcoming)
        assertEquals("数字电路 · 上课中", presentation.title)
        assertTrue(presentation.text.startsWith("F3b-212"))
        assertTrue(presentation.text.contains("14:00开始"))
        assertTrue(presentation.text.contains("15:35下课"))
        assertEquals("上课中", presentation.subText)
    }

    @Test
    fun `live label is compact and can be overridden`() {
        assertEquals(
            "C++",
            ClassNotificationPresenter.liveLabel(
                course.copy(name = "人工智能II：C++编程基础"),
            ),
        )
        assertEquals(
            "程序设计",
            ClassNotificationPresenter.liveLabel(
                course.copy(liveDisplayName = "程序设计"),
            ),
        )
    }
}
