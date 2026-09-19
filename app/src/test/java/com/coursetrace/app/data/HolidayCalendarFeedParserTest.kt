package com.coursetrace.app.data

import com.coursetrace.app.model.CalendarRuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HolidayCalendarFeedParserTest {
    @Test
    fun parsesStrictHttpsCalendarFeed() {
        val raw = """
            {
              "schemaVersion": 1,
              "sourceName": "测试源",
              "sourcePage": "https://example.com/notice",
              "updatedAt": "2026-09-19",
              "rules": [
                {"id":"day-off","date":"2026-10-01","type":"NO_CLASS","title":"放假"},
                {"id":"make-up","date":"2026-10-10","type":"FOLLOW_DATE","title":"补课","sourceDate":"2026-10-05"}
              ]
            }
        """.trimIndent()

        val (_, rules) = HolidayCalendarFeedParser.parse(raw, "https://example.com/calendar.json")

        assertEquals(2, rules.size)
        assertEquals(CalendarRuleType.FOLLOW_DATE, rules.last().type)
        assertEquals("2026-10-05", rules.last().sourceDate)
    }

    @Test
    fun rejectsInsecureOrAmbiguousSources() {
        assertThrows(IllegalArgumentException::class.java) {
            HolidayCalendarFeedParser.requireHttps("http://example.com/calendar.json")
        }
        val missingSourceDate = """
            {"schemaVersion":1,"sourceName":"测试源","sourcePage":"https://example.com/notice","updatedAt":"2026-09-19","rules":[{"id":"bad","date":"2026-10-10","type":"FOLLOW_DATE","title":"补课"}]}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            HolidayCalendarFeedParser.parse(missingSourceDate, "https://example.com/calendar.json")
        }
    }

    @Test
    fun rejectsDuplicateIdsAndInvalidMetadataDates() {
        val duplicateIds = """
            {"schemaVersion":1,"sourceName":"测试源","sourcePage":"https://example.com/notice","updatedAt":"2026-09-19","rules":[{"id":"same","date":"2026-10-01","type":"NO_CLASS","title":"放假"},{"id":"same","date":"2026-10-02","type":"NO_CLASS","title":"放假"}]}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            HolidayCalendarFeedParser.parse(duplicateIds, "https://example.com/calendar.json")
        }

        val invalidUpdatedAt = """
            {"schemaVersion":1,"sourceName":"测试源","sourcePage":"https://example.com/notice","updatedAt":"今天","rules":[]}
        """.trimIndent()
        assertThrows(java.time.format.DateTimeParseException::class.java) {
            HolidayCalendarFeedParser.parse(invalidUpdatedAt, "https://example.com/calendar.json")
        }
    }
}
