package com.coursetrace.app.data

import com.coursetrace.app.model.ScheduleTimeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetablePromptsTest {
    @Test
    fun allDeepSeekOptionsUseTheImportContractAndCurrentPeriodProfile() {
        val options = TimetablePrompts.deepSeekOptions(ScheduleTimeProfile())

        assertEquals(listOf("general", "periods", "scan"), options.map { it.id })
        options.forEach { option ->
            assertTrue(option.prompt.contains("第1节 08:50-09:35"))
            assertTrue(option.prompt.contains("第11节 20:50-21:35"))
            assertTrue(option.prompt.contains("\"slots\""))
            assertTrue(option.prompt.contains("\"unscheduledCourses\""))
            assertTrue(option.prompt.contains("termStartDate"))
            assertTrue(option.prompt.contains("不要 Markdown"))
        }
    }
}
