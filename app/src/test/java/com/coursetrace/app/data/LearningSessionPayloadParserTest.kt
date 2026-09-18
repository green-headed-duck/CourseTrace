package com.coursetrace.app.data

import com.coursetrace.app.model.LearningEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSessionPayloadParserTest {
    @Test
    fun parsesStructuredChatGptPayloadInsideMarkdownFence() {
        val payload = LearningSessionPayloadParser.parse(
            """
            ```json
            {
              "summary": "完成卡诺图化简",
              "events": [
                {"kind": "QUESTION", "content": "无关项怎么使用？", "occurredAt": "2026-09-18T10:00:00+08:00"},
                {"kind": "痛点", "content": "容易漏圈最大项"}
              ],
              "rawTranscript": "我：这个圈可以跨边吗？\nGPT：可以。",
              "transcriptComplete": true
            }
            ```
            """.trimIndent(),
        )

        assertEquals("完成卡诺图化简", payload.summary)
        assertEquals(2, payload.events.size)
        assertEquals(LearningEventKind.QUESTION, payload.events[0].kind)
        assertEquals(LearningEventKind.PAIN_POINT, payload.events[1].kind)
        assertTrue(payload.transcriptComplete)
    }

    @Test
    fun acceptsLegacySummaryLists() {
        val payload = LearningSessionPayloadParser.parse(
            """{"summary":"复习完成","wrong_answers":["把异或写成或"],"next_actions":["重做第 3 题"]}""",
        )

        assertEquals(2, payload.events.size)
        assertEquals(LearningEventKind.WRONG_ANSWER, payload.events[0].kind)
        assertEquals(LearningEventKind.NOTE, payload.events[1].kind)
        assertFalse(payload.transcriptComplete)
    }
}
