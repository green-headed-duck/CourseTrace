package com.coursetrace.app.data

import com.coursetrace.app.model.LearningEventKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class ImportedLearningEvent(
    val kind: LearningEventKind,
    val content: String,
    val occurredAt: String? = null,
)

data class LearningSessionImportPayload(
    val summary: String = "",
    val events: List<ImportedLearningEvent> = emptyList(),
    val rawTranscript: String = "",
    val transcriptComplete: Boolean = false,
)

object LearningSessionPayloadParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(input: String): LearningSessionImportPayload {
        val cleaned = input.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "没有找到有效的课堂记录 JSON" }
        val root = json.parseToJsonElement(cleaned.substring(start, end + 1)) as? JsonObject
            ?: error("课堂记录必须是一个 JSON 对象")

        val explicitEvents = (root["events"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val content = item.text("content", "text", "detail")?.trim().orEmpty()
            if (content.isBlank()) return@mapNotNull null
            ImportedLearningEvent(
                kind = parseKind(item.text("kind", "type")),
                content = content,
                occurredAt = item.text("occurredAt", "occurred_at", "timestamp"),
            )
        }
        val events = if (explicitEvents.isNotEmpty()) explicitEvents else legacyEvents(root)
        val transcript = root.transcript("rawTranscript", "raw_transcript", "transcript", "transcriptChunks", "transcript_chunks")
        val payload = LearningSessionImportPayload(
            summary = root.text("summary", "classSummary", "class_summary").orEmpty().trim(),
            events = events,
            rawTranscript = transcript.trim(),
            transcriptComplete = root.boolean("transcriptComplete", "transcript_complete") ?: false,
        )
        require(payload.summary.isNotBlank() || payload.events.isNotEmpty() || payload.rawTranscript.isNotBlank()) {
            "JSON 中没有摘要、事件或课堂原文"
        }
        return payload
    }

    private fun legacyEvents(root: JsonObject): List<ImportedLearningEvent> = buildList {
        val mappings = listOf(
            "questions" to LearningEventKind.QUESTION,
            "pain_points" to LearningEventKind.PAIN_POINT,
            "wrong_answers" to LearningEventKind.WRONG_ANSWER,
            "progress" to LearningEventKind.PROGRESS,
            "decisions" to LearningEventKind.DECISION,
            "notes" to LearningEventKind.NOTE,
            "unresolved" to LearningEventKind.PAIN_POINT,
            "resolved" to LearningEventKind.PROGRESS,
            "next_actions" to LearningEventKind.NOTE,
        )
        mappings.forEach { (key, kind) ->
            root.strings(key).forEach { content -> add(ImportedLearningEvent(kind, content)) }
        }
    }

    private fun parseKind(value: String?): LearningEventKind {
        val normalized = value.orEmpty().trim().uppercase().replace('-', '_').replace(' ', '_')
        return when (normalized) {
            "TRANSCRIPT", "原文" -> LearningEventKind.TRANSCRIPT
            "QUESTION", "问题" -> LearningEventKind.QUESTION
            "PAIN_POINT", "PAINPOINT", "痛点" -> LearningEventKind.PAIN_POINT
            "WRONG_ANSWER", "WRONGANSWER", "错题", "错误" -> LearningEventKind.WRONG_ANSWER
            "PROGRESS", "进度" -> LearningEventKind.PROGRESS
            "DECISION", "结论", "决定" -> LearningEventKind.DECISION
            else -> LearningEventKind.NOTE
        }
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.boolean(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        val primitive = this[key] as? JsonPrimitive
        primitive?.booleanOrNull ?: primitive?.contentOrNull?.toBooleanStrictOrNull()
    }

    private fun JsonObject.strings(key: String): List<String> = when (val value = this[key]) {
        is JsonArray -> value.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element.text("content", "text", "detail")
                else -> null
            }?.trim()?.takeIf(String::isNotBlank)
        }
        is JsonPrimitive -> listOfNotNull(value.contentOrNull?.trim()?.takeIf(String::isNotBlank))
        else -> emptyList()
    }

    private fun JsonObject.transcript(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            is JsonPrimitive -> value.contentOrNull
            is JsonArray -> value.mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.contentOrNull
                    is JsonObject -> element.text("content", "text")
                    else -> null
                }
            }.joinToString("\n").takeIf(String::isNotBlank)
            else -> null
        }
    }.orEmpty()
}
