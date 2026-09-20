package com.coursetrace.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetablePayloadParserTest {
    @Test
    fun acceptsChatGptMarkdownFenceAndKeepsConfidence() {
        val payload = TimetablePayloadParser.parse(
            """```json
                {"slots":[{"courseName":"FPGA","dayOfWeek":3,"startTime":"14:00","endTime":"15:35","startWeek":1,"endWeek":8,"weekPattern":"EVERY","confidence":0.76}],"warnings":["教室不清晰"]}
                ```
            """.trimIndent(),
        )

        assertEquals("FPGA", payload.slots.single().courseName)
        assertEquals(0.76f, payload.slots.single().confidence)
        assertEquals("教室不清晰", payload.warnings.single())
    }

    @Test
    fun convertsInternationalCampusPeriodsToExactTimes() {
        val payload = TimetablePayloadParser.parse(
            """{"slots":[{"courseName":"数字电路","dayOfWeek":1,"startPeriod":1,"endPeriod":2}]}""",
        )

        val slot = payload.slots.single()
        assertEquals("08:50", slot.startTime)
        assertEquals("10:25", slot.endTime)
        assertEquals(1, slot.startPeriod)
        assertEquals(2, slot.endPeriod)
    }

    @Test
    fun periodWinsWhenModelReturnsAnotherCampusTime() {
        val payload = TimetablePayloadParser.parse(
            """{"slots":[{"courseName":"FPGA","dayOfWeek":3,"startPeriod":5,"endPeriod":6,"startTime":"14:30","endTime":"16:10"}]}""",
        )

        val slot = payload.slots.single()
        assertEquals("14:00", slot.startTime)
        assertEquals("15:35", slot.endTime)
        assertTrue(slot.warnings.single().contains("已按节次修正"))
    }

    @Test
    fun preservesExplicitDiscontinuousWeeksAndTerm() {
        val payload = TimetablePayloadParser.parse(
            """{"termName":"2026-2027学年第1学期","termStartDate":"2026-08-31","termWeekCount":22,"slots":[{"courseName":"微积分","dayOfWeek":1,"startPeriod":1,"endPeriod":2,"weeks":[18,3,15,3]}]}""",
        )

        assertEquals(listOf(3, 15, 18), payload.slots.single().weeks)
        assertEquals(3, payload.slots.single().startWeek)
        assertEquals(18, payload.slots.single().endWeek)
        assertEquals("2026-08-31", payload.termStartDate)
    }

    @Test
    fun acceptsNullWeekRangeFromCompatibleModelsAndMarksItForReview() {
        val payload = TimetablePayloadParser.parse(
            """{"termWeekCount":22,"slots":[{"courseName":"大学英语","teacher":null,"dayOfWeek":2,"startPeriod":3,"endPeriod":4,"room":null,"startWeek":null,"endWeek":null,"weekPattern":null,"weeks":null,"confidence":null,"warnings":null}]}""",
        )

        val slot = payload.slots.single()
        assertEquals(1, slot.startWeek)
        assertEquals(22, slot.endWeek)
        assertEquals("", slot.teacher)
        assertEquals("", slot.room)
        assertTrue(slot.weeks.isEmpty())
        assertTrue(slot.warnings.single().contains("暂按整学期处理"))
    }
}
