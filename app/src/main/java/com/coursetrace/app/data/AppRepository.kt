package com.coursetrace.app.data

import android.content.Context
import com.coursetrace.app.model.AppPreferences
import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import com.coursetrace.app.model.ImportDraft
import com.coursetrace.app.model.LearningEvent
import com.coursetrace.app.model.LearningEventKind
import com.coursetrace.app.model.LearningSession
import com.coursetrace.app.model.MaterialItem
import com.coursetrace.app.model.NextMaterialPrediction
import com.coursetrace.app.model.StudyProject
import com.coursetrace.app.model.Term
import com.coursetrace.app.domain.AcademicTermPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.io.File
import java.time.OffsetDateTime
import com.coursetrace.app.widget.NextClassWidgetProvider

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val recordsDir = File(context.filesDir, "records")
    private val stateFile = File(recordsDir, "state.json")
    private val tempFile = File(recordsDir, "state.json.tmp")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var repairedTermOnLoad = false
    val gitHistory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GitHistoryService(recordsDir) }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                runCatching {
                    when {
                        !stateFile.exists() -> persist(_state.value, "初始化课迹本地仓库")
                        repairedTermOnLoad -> {
                            persist(_state.value, "修复学期教学周起点")
                            repairedTermOnLoad = false
                        }
                    }
                }
            }
        }
    }

    private fun defaultState(): AppState {
        val term = AcademicTermPolicy.defaultTerm()
        return AppState(terms = listOf(term), activeTermId = term.id)
    }

    private fun load(): AppState = runCatching {
        if (!stateFile.exists()) return@runCatching defaultState()
        val decoded = json.decodeFromString<AppState>(stateFile.readText())
        AcademicTermPolicy.repairKnown2026Term(decoded).also { repaired ->
            repairedTermOnLoad = repaired != decoded
        }
    }.getOrElse { defaultState() }

    suspend fun reloadAfterRestore() = mutex.withLock {
        val restored = load()
        _state.value = restored
        persist(
            restored,
            if (repairedTermOnLoad) "从加密备份恢复并修复学期教学周起点" else "从加密备份恢复",
        )
        repairedTermOnLoad = false
    }

    suspend fun update(message: String, transform: (AppState) -> AppState) = mutex.withLock {
        val next = transform(_state.value)
        _state.value = next
        persist(next, message)
    }

    private suspend fun persist(state: AppState, message: String) {
        recordsDir.mkdirs()
        tempFile.writeText(json.encodeToString(state))
        if (stateFile.exists() && !stateFile.delete()) error("无法替换本地记录")
        if (!tempFile.renameTo(stateFile)) error("无法保存本地记录")
        gitHistory.commit(message)
        NextClassWidgetProvider.updateAll(appContext, state)
    }

    suspend fun updatePreferences(preferences: AppPreferences) =
        update("更新应用设置") { it.copy(preferences = preferences) }

    suspend fun addTerm(term: Term, makeActive: Boolean = true) =
        update("新增学期：${term.name}") {
            it.copy(
                terms = it.terms + term,
                activeTermId = if (makeActive) term.id else it.activeTermId,
            )
        }

    suspend fun updateTerm(term: Term) = update("校准学期教学周：${term.name}") { state ->
        require(state.terms.any { it.id == term.id }) { "学期不存在" }
        state.copy(terms = state.terms.map { if (it.id == term.id) term else it })
    }

    suspend fun setActiveTerm(termId: String) = update("切换当前学期") { state ->
        require(state.terms.any { it.id == termId }) { "学期不存在" }
        state.copy(activeTermId = termId)
    }

    suspend fun archiveTerm(termId: String) = update("归档学期") { state ->
        val updated = state.terms.map { if (it.id == termId) it.copy(archived = true) else it }
        val nextActive = if (state.activeTermId == termId) updated.firstOrNull { !it.archived }?.id else state.activeTermId
        state.copy(terms = updated, activeTermId = nextActive)
    }

    suspend fun upsertCourse(course: Course, slots: List<CourseSlot> = emptyList()) =
        update("更新课程：${course.name}") { old ->
            old.copy(
                courses = old.courses.filterNot { it.id == course.id } + course,
                slots = old.slots.filterNot { existing -> slots.any { it.id == existing.id } } + slots,
            )
        }

    suspend fun archiveCourse(courseId: String) = update("课程移出课表") { state ->
        state.copy(courses = state.courses.map { course ->
            if (course.id == courseId) course.copy(archived = true) else course
        })
    }

    suspend fun addProject(project: StudyProject) =
        update("新增自学项目：${project.name}") { it.copy(projects = it.projects + project) }

    suspend fun updateProject(project: StudyProject) =
        update("更新自学进度：${project.name}") {
            it.copy(projects = it.projects.map { old -> if (old.id == project.id) project else old })
        }

    suspend fun addMaterial(item: MaterialItem) =
        update("添加资料：${item.title}") { it.copy(materials = it.materials + item) }

    suspend fun startSession(ownerId: String, ownerType: String, title: String): LearningSession {
        val session = LearningSession(
            ownerId = ownerId,
            ownerType = ownerType,
            startedAt = OffsetDateTime.now().toString(),
            title = title,
        )
        update("开始学习记录：$title") { it.copy(sessions = it.sessions + session) }
        return session
    }

    suspend fun appendEvent(
        sessionId: String,
        kind: LearningEventKind,
        content: String,
        source: String = "local",
    ) {
        update("记录课堂事件") { state ->
            val sequence = state.events.count { it.sessionId == sessionId } + 1
            val event = LearningEvent(
                sessionId = sessionId,
                timestamp = OffsetDateTime.now().toString(),
                kind = kind,
                content = content,
                sequence = sequence,
                source = source,
            )
            state.copy(events = state.events + event)
        }
    }

    suspend fun importLearningSessionPayload(
        sessionId: String,
        payload: LearningSessionImportPayload,
        finish: Boolean,
    ) {
        update(if (finish) "导入并结束 ChatGPT 课堂记录" else "导入 ChatGPT 课堂记录") { state ->
            require(state.sessions.any { it.id == sessionId }) { "当前记录不存在" }
            val now = OffsetDateTime.now().toString()
            val firstSequence = state.events.count { it.sessionId == sessionId } + 1
            val importedEvents = payload.events.mapIndexed { index, imported ->
                LearningEvent(
                    sessionId = sessionId,
                    timestamp = imported.occurredAt?.takeIf(String::isNotBlank) ?: now,
                    kind = imported.kind,
                    content = imported.content.trim(),
                    sequence = firstSequence + index,
                    source = "chatgpt_mobile",
                )
            }
            state.copy(
                events = state.events + importedEvents,
                sessions = state.sessions.map { session ->
                    if (session.id != sessionId) session else session.copy(
                        endedAt = if (finish) now else session.endedAt,
                        summary = payload.summary.ifBlank { session.summary },
                        rawTranscript = mergeImportedText(session.rawTranscript, payload.rawTranscript),
                        transcriptComplete = payload.transcriptComplete,
                    )
                },
            )
        }
    }

    private fun mergeImportedText(existing: String, imported: String): String = when {
        imported.isBlank() -> existing
        existing.isBlank() || existing.trim() == imported.trim() -> imported.trim()
        else -> "${existing.trim()}\n\n${imported.trim()}"
    }

    suspend fun finishSession(
        sessionId: String,
        summary: String,
        transcript: String,
        transcriptComplete: Boolean,
    ) = update("结束学习记录") { state ->
        state.copy(sessions = state.sessions.map {
            if (it.id == sessionId) it.copy(
                endedAt = OffsetDateTime.now().toString(),
                summary = summary,
                rawTranscript = transcript,
                transcriptComplete = transcriptComplete,
            ) else it
        })
    }

    suspend fun savePrediction(prediction: NextMaterialPrediction) =
        update("更新下节课资料预测") { state ->
            state.copy(predictions = state.predictions.filterNot { it.ownerId == prediction.ownerId } + prediction)
        }

    suspend fun saveImportDraft(draft: ImportDraft) =
        update("创建课表导入草稿：${draft.sourceName}") { state ->
            if (draft.sourceFingerprint.isNotBlank()) {
                require(draft.sourceFingerprint !in state.appliedImportFingerprints) {
                    "这个 PDF 已经成功导入过，无需重复导入"
                }
                if (state.importDrafts.any { it.sourceFingerprint == draft.sourceFingerprint }) {
                    return@update state
                }
            }
            state.copy(importDrafts = state.importDrafts + draft)
        }

    suspend fun clearImportDrafts() =
        update("清理已处理的课表导入草稿") { it.copy(importDrafts = emptyList()) }

    suspend fun commitImportDraft(draftId: String) = update("确认导入课表") { state ->
        val draft = state.importDrafts.find { it.id == draftId }
            ?: error("该导入草稿已被处理，请勿重复点击")
        require(draft.sourceFingerprint.isBlank() || draft.sourceFingerprint !in state.appliedImportFingerprints) {
            "这个 PDF 已经成功导入过，无需重复导入"
        }
        val currentTermId = state.activeTermId ?: state.terms.firstOrNull { !it.archived }?.id
            ?: error("请先创建学期")
        val currentTerm = state.terms.first { it.id == currentTermId }
        val hasCurrentCourses = state.courses.any { it.termId == currentTermId }
        // Model-provided dates are suggestions only. An import never creates or silently
        // switches terms or rewrites the teaching-week anchor chosen by the user.
        val importedTerm = currentTerm.copy(
            name = if (!hasCurrentCourses) {
                draft.termName?.takeIf { it.isNotBlank() } ?: currentTerm.name
            } else {
                currentTerm.name
            },
            startDate = currentTerm.startDate,
            weekCount = maxOf(currentTerm.weekCount, draft.termWeekCount ?: currentTerm.weekCount),
        )
        val terms = state.terms.map { term ->
            if (term.id == currentTermId) importedTerm else term
        }
        val termId = currentTermId
        val courses = state.courses.toMutableList()
        val slots = state.slots.toMutableList()
        draft.slots.forEach { imported ->
            val course = courses.find { it.termId == termId && !it.archived && it.name.trim() == imported.courseName.trim() }
                ?: Course(termId = termId, name = imported.courseName, teacher = imported.teacher).also(courses::add)
            val candidate = CourseSlot(
                courseId = course.id,
                dayOfWeek = imported.dayOfWeek,
                startTime = imported.startTime,
                endTime = imported.endTime,
                startPeriod = imported.startPeriod,
                endPeriod = imported.endPeriod,
                room = imported.room,
                startWeek = imported.startWeek,
                endWeek = imported.endWeek,
                weekPattern = imported.weekPattern,
                weeks = imported.weeks,
            )
            val duplicate = slots.any {
                it.courseId == candidate.courseId && it.dayOfWeek == candidate.dayOfWeek &&
                    it.startTime == candidate.startTime && it.startWeek == candidate.startWeek &&
                    it.endWeek == candidate.endWeek && it.weekPattern == candidate.weekPattern &&
                    it.weeks == candidate.weeks
            }
            if (!duplicate) slots += candidate
        }
        draft.unscheduledCourses.forEach { imported ->
            if (courses.none { it.termId == termId && !it.archived && it.name.trim() == imported.courseName.trim() }) {
                val weekText = imported.weeks.distinct().sorted().joinToString(",")
                courses += Course(
                    termId = termId,
                    name = imported.courseName.trim(),
                    teacher = imported.teacher.trim(),
                    notes = listOfNotNull(
                        imported.notes.trim().takeIf(String::isNotBlank),
                        weekText.takeIf(String::isNotBlank)?.let { "教学周：$it" },
                        "无固定星期和节次，不创建定时提醒",
                    ).joinToString("\n"),
                )
            }
        }
        state.copy(
            terms = terms,
            activeTermId = currentTermId,
            courses = courses,
            slots = slots,
            importDrafts = state.importDrafts.filterNot { it.id == draftId },
            appliedImportFingerprints = if (draft.sourceFingerprint.isBlank()) {
                state.appliedImportFingerprints
            } else {
                (state.appliedImportFingerprints + draft.sourceFingerprint).distinct().takeLast(100)
            },
        )
    }

    suspend fun applyRelayChanges(changes: List<RelayChangeDto>) = update("同步 ChatGPT 学习记录") { original ->
        var state = original
        val alreadyApplied = state.appliedRelayChangeIds.toMutableSet()
        changes.sortedBy { it.sequence }.forEach { change ->
            if (!alreadyApplied.add(change.id)) return@forEach
            val payload = change.payload
            when (change.type) {
                "SESSION_STARTED" -> {
                    val id = payload.string("id") ?: change.id
                    if (state.sessions.none { it.id == id }) {
                        state = state.copy(sessions = state.sessions + LearningSession(
                            id = id,
                            ownerId = payload.string("ownerId").orEmpty(),
                            ownerType = payload.string("ownerType") ?: "course",
                            startedAt = payload.string("startedAt") ?: change.createdAt,
                            title = payload.string("title") ?: "ChatGPT 学习记录",
                        ))
                    }
                }
                "LEARNING_EVENT" -> {
                    if (state.events.none { it.id == change.id }) {
                        val sessionId = payload.string("sessionId").orEmpty()
                        state = state.copy(events = state.events + LearningEvent(
                            id = change.id,
                            sessionId = sessionId,
                            timestamp = payload.string("occurredAt") ?: change.createdAt,
                            kind = runCatching {
                                LearningEventKind.valueOf(payload.string("kind") ?: "NOTE")
                            }.getOrDefault(LearningEventKind.NOTE),
                            content = payload.string("content").orEmpty(),
                            sequence = state.events.count { it.sessionId == sessionId } + 1,
                            source = "chatgpt",
                        ))
                    }
                }
                "TRANSCRIPT_CHUNK" -> {
                    val sessionId = payload.string("sessionId").orEmpty()
                    val content = payload.string("content").orEmpty()
                    if (state.events.none { it.id == change.id }) {
                        state = state.copy(
                            events = state.events + LearningEvent(
                                id = change.id,
                                sessionId = sessionId,
                                timestamp = payload.string("occurredAt") ?: change.createdAt,
                                kind = LearningEventKind.TRANSCRIPT,
                                content = content,
                                sequence = state.events.count { it.sessionId == sessionId } + 1,
                                source = "chatgpt",
                            ),
                            sessions = state.sessions.map {
                                if (it.id == sessionId) it.copy(rawTranscript = listOf(it.rawTranscript, content).filter(String::isNotBlank).joinToString("\n")) else it
                            },
                        )
                    }
                }
                "SESSION_FINISHED" -> {
                    val sessionId = payload.string("sessionId").orEmpty()
                    state = state.copy(sessions = state.sessions.map {
                        if (it.id == sessionId) it.copy(
                            endedAt = payload.string("endedAt") ?: change.createdAt,
                            summary = payload.string("summary").orEmpty(),
                            transcriptComplete = payload["transcriptComplete"]?.jsonPrimitive?.booleanOrNull ?: false,
                        ) else it
                    })
                }
                "TIMETABLE_IMPORT_DRAFT" -> {
                    if (state.importDrafts.none { it.id == change.id }) {
                        val slots = payload["slots"]?.let {
                            runCatching { json.decodeFromString<List<com.coursetrace.app.model.ImportedSlot>>(it.toString()) }.getOrDefault(emptyList())
                        }.orEmpty()
                        val warnings = payload["warnings"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                        state = state.copy(importDrafts = state.importDrafts + ImportDraft(
                            id = change.id,
                            createdAt = change.createdAt,
                            source = com.coursetrace.app.model.ImportSource.CHATGPT_MOBILE,
                            sourceName = "ChatGPT Mobile",
                            slots = slots,
                            warnings = warnings,
                        ))
                    }
                }
                "NEXT_MATERIAL_PREDICTION" -> {
                    val ownerId = payload.string("owner_id").orEmpty()
                    val prediction = NextMaterialPrediction(
                        id = change.id,
                        ownerId = ownerId,
                        generatedAt = change.createdAt,
                        title = payload.string("title").orEmpty(),
                        reason = payload.string("reason").orEmpty(),
                        confidence = payload["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f,
                        materialIds = payload["material_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                        sourceSessionId = payload.string("source_session_id"),
                    )
                    state = state.copy(predictions = state.predictions.filterNot { it.ownerId == ownerId } + prediction)
                }
            }
        }
        state.copy(appliedRelayChangeIds = alreadyApplied.toList().takeLast(1_000))
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
}
