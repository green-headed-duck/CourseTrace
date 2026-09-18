package com.coursetrace.app

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coursetrace.app.data.BackupManager
import com.coursetrace.app.data.LearningSessionPayloadParser
import com.coursetrace.app.data.PdfImportService
import com.coursetrace.app.data.TimetablePayloadParser
import com.coursetrace.app.data.RelaySyncScheduler
import com.coursetrace.app.data.RelaySyncService
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.domain.AcademicTermPolicy
import com.coursetrace.app.model.ApiProfile
import com.coursetrace.app.model.AppPreferences
import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import com.coursetrace.app.model.ImportDraft
import com.coursetrace.app.model.ImportSource
import com.coursetrace.app.model.LearningEventKind
import com.coursetrace.app.model.LearningSession
import com.coursetrace.app.model.MaterialItem
import com.coursetrace.app.model.MaterialKind
import com.coursetrace.app.model.StudyProject
import com.coursetrace.app.model.Term
import com.coursetrace.app.model.WeekPattern
import com.coursetrace.app.notifications.EarlyAlarmService
import com.coursetrace.app.notifications.NotificationScheduler
import com.coursetrace.app.update.UpdateCheckResult
import com.coursetrace.app.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.security.MessageDigest
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.LocalTime

data class WorkStatus(
    val busy: Boolean = false,
    val message: String? = null,
    val progress: Float? = null,
    val detail: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CourseTraceApplication
    val appState: StateFlow<AppState> = app.repository.state
    private val _workStatus = MutableStateFlow(WorkStatus())
    val workStatus = _workStatus.asStateFlow()
    private val _activeSession = MutableStateFlow<LearningSession?>(null)
    val activeSession = _activeSession.asStateFlow()
    private val _openCourseId = MutableStateFlow<String?>(null)
    val openCourseId = _openCourseId.asStateFlow()
    private val timetableImportMutex = Mutex()

    fun requestOpenCourse(courseId: String) { _openCourseId.value = courseId }
    fun consumeOpenCourse() { _openCourseId.value = null }

    fun clearMessage() {
        _workStatus.value = _workStatus.value.copy(message = null)
    }

    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            runCatching { app.repository.updatePreferences(transform(appState.value.preferences)) }
                .onSuccess {
                    NotificationScheduler(app).reschedule(appState.value)
                }
                .onFailure { showError(it) }
        }
    }

    fun saveApiProfile(profile: ApiProfile, apiKey: String?) {
        viewModelScope.launch {
            runCatching {
                if (apiKey != null) app.secureSettings.putApiKey(apiKey.trim())
                app.repository.updatePreferences(appState.value.preferences.copy(apiProfile = profile))
            }.onSuccess { _workStatus.value = WorkStatus(message = "接口设置已安全保存") }
                .onFailure { showError(it) }
        }
    }

    fun hasApiKey(): Boolean = app.secureSettings.hasApiKey()

    fun hasRelayToken(): Boolean = app.secureSettings.hasRelayToken()

    fun saveChatGptLink(enabled: Boolean, relayUrl: String, token: String?) {
        viewModelScope.launch {
            runCatching {
                if (token != null) app.secureSettings.putRelayToken(token.trim())
                if (enabled) {
                    require(relayUrl.startsWith("https://")) {
                        "中继必须使用 HTTPS"
                    }
                    require(app.secureSettings.hasRelayToken()) { "请输入至少 32 位配对令牌" }
                }
                val preferences = appState.value.preferences
                app.repository.updatePreferences(preferences.copy(
                    chatGptLink = preferences.chatGptLink.copy(enabled = enabled, relayUrl = relayUrl.trimEnd('/')),
                ))
                RelaySyncScheduler.update(app, enabled)
            }.onSuccess {
                _workStatus.value = WorkStatus(message = if (enabled) "ChatGPT 联动已启用" else "ChatGPT 联动已关闭")
                if (enabled) syncChatGptNow()
            }.onFailure(::showError)
        }
    }

    fun syncChatGptNow() {
        viewModelScope.launch {
            _workStatus.value = WorkStatus(busy = true, message = "正在与 ChatGPT 中继同步…")
            RelaySyncService(app.repository, app.secureSettings).sync().fold(
                onSuccess = { _workStatus.value = WorkStatus(message = "同步完成，收到 $it 条新记录") },
                onFailure = ::showError,
            )
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val preferences = appState.value.preferences
            if (preferences.updateManifestUrl.isBlank()) {
                _workStatus.value = WorkStatus(message = "请先填写 HTTPS 更新清单地址")
                return@launch
            }
            _workStatus.value = WorkStatus(busy = true, message = "正在检查更新…")
            val manager = UpdateManager(app)
            manager.check(preferences.updateManifestUrl, preferences.updateChannel).fold(
                onSuccess = { result ->
                    when (result) {
                        UpdateCheckResult.UpToDate -> _workStatus.value = WorkStatus(message = "当前已是最新版本")
                        is UpdateCheckResult.Available -> {
                            _workStatus.value = WorkStatus(busy = true, message = "发现 ${result.manifest.versionName}，正在安全下载…")
                            manager.downloadAndInstall(result.manifest).fold(
                                onSuccess = { _workStatus.value = WorkStatus(message = "校验完成，已交给系统安装器") },
                                onFailure = ::showError,
                            )
                        }
                    }
                },
                onFailure = ::showError,
            )
        }
    }

    fun exportEncryptedBackup(uri: Uri, recoveryPassword: CharArray) {
        viewModelScope.launch {
            _workStatus.value = WorkStatus(busy = true, message = "正在创建加密备份…")
            runCatching { BackupManager(app).exportEncrypted(uri, recoveryPassword) }
                .onSuccess {
                    _workStatus.value = WorkStatus(message = "备份完成：${it.fileCount} 个文件，原始数据 ${it.uncompressedBytes / 1024} KiB")
                }
                .onFailure(::showError)
        }
    }

    fun restoreEncryptedBackup(uri: Uri, recoveryPassword: CharArray) {
        viewModelScope.launch {
            _workStatus.value = WorkStatus(busy = true, message = "正在校验并恢复备份…")
            runCatching {
                val result = BackupManager(app).restoreEncrypted(uri, recoveryPassword)
                app.repository.reloadAfterRestore()
                NotificationScheduler(app).reschedule(app.repository.state.value)
                result
            }.onSuccess {
                _workStatus.value = WorkStatus(message = "已恢复 ${it.fileCount} 个文件；恢复前数据仍保留在应用私有目录")
            }.onFailure(::showError)
        }
    }

    fun testApi(profile: ApiProfile, apiKeyInput: String?) {
        viewModelScope.launch {
            _workStatus.value = WorkStatus(busy = true, message = "正在测试接口…")
            val key = apiKeyInput?.takeIf { it.isNotBlank() } ?: app.secureSettings.getApiKey().orEmpty()
            app.apiClient.test(profile, key)
                .onSuccess { _workStatus.value = WorkStatus(message = "连接成功：${it.take(80)}") }
                .onFailure { showError(it) }
        }
    }

    fun importPdf(uri: Uri) {
        if (!timetableImportMutex.tryLock()) {
            _workStatus.value = _workStatus.value.copy(
                busy = true,
                detail = "已忽略重复导入操作，请等待当前任务完成",
            )
            return
        }
        viewModelScope.launch {
            try {
                _workStatus.value = WorkStatus(busy = true, message = "正在准备导入…", progress = 0f)
                val name = DocumentFile.fromSingleUri(app, uri)?.name ?: "课程表.pdf"
                val key = app.secureSettings.getApiKey().orEmpty()
                val preferences = appState.value.preferences
                runCatching {
                    val draft = app.pdfImportService.import(
                        uri = uri,
                        sourceName = name,
                        profile = preferences.apiProfile,
                        apiKey = key,
                        timeProfile = preferences.scheduleTimeProfile,
                        onProgress = ::showPdfImportProgress,
                    ).getOrThrow()
                    app.repository.saveImportDraft(draft)
                    draft
                }.onSuccess { draft ->
                    _workStatus.value = WorkStatus(
                        message = "识别完成，请核对 ${draft.slots.size} 条课程后再确认导入",
                    )
                }.onFailure(::showError)
            } finally {
                timetableImportMutex.unlock()
            }
        }
    }

    private fun showPdfImportProgress(progress: PdfImportService.Progress) {
        _workStatus.value = WorkStatus(
            busy = true,
            message = progress.message,
            progress = progress.fraction,
            detail = progress.detail,
        )
    }

    fun importChatGptShare(text: String) {
        viewModelScope.launch {
            val draft = runCatching {
                val payload = TimetablePayloadParser.parse(text, appState.value.preferences.scheduleTimeProfile)
                ImportDraft(
                    createdAt = OffsetDateTime.now().toString(),
                    source = ImportSource.CHATGPT_MOBILE,
                    sourceName = "ChatGPT Mobile 分享",
                    slots = payload.slots,
                    unscheduledCourses = payload.unscheduledCourses,
                    warnings = payload.warnings,
                    termName = payload.termName,
                    termStartDate = payload.termStartDate,
                    termWeekCount = payload.termWeekCount,
                )
            }.getOrNull()
            if (draft != null) {
                app.repository.saveImportDraft(draft)
                _workStatus.value = WorkStatus(message = "已接收 ChatGPT 课表草稿，请先核对")
                return@launch
            }
            runCatching {
                val now = java.time.LocalDateTime.now()
                val current = ScheduleEngine.classesOn(appState.value, now.toLocalDate())
                    .find { now >= it.start.minusMinutes(30) && now <= it.end.plusMinutes(60) }
                val ownerId: String
                val ownerType: String
                val title: String
                if (current != null) {
                    ownerId = current.course.id
                    ownerType = "course"
                    title = current.course.name
                } else {
                    val existing = appState.value.projects.find { it.name == "ChatGPT 收件箱" }
                    val project = existing ?: StudyProject(
                        name = "ChatGPT 收件箱",
                        description = "无法自动匹配课程的手机端分享记录",
                    ).also { app.repository.addProject(it) }
                    ownerId = project.id
                    ownerType = "project"
                    title = project.name
                }
                val session = app.repository.startSession(ownerId, ownerType, "$title · ChatGPT 分享")
                app.repository.finishSession(
                    sessionId = session.id,
                    summary = "来自 ChatGPT Mobile 的分享文本",
                    transcript = text,
                    transcriptComplete = false,
                )
            }.onSuccess {
                _workStatus.value = WorkStatus(message = "已把分享原文保存到当前课程或 ChatGPT 收件箱；完整性暂标为未确认")
            }.onFailure(::showError)
        }
    }

    fun commitImport(draftId: String) {
        if (!timetableImportMutex.tryLock()) {
            _workStatus.value = _workStatus.value.copy(
                busy = true,
                detail = "已忽略重复确认操作，请等待当前任务完成",
            )
            return
        }
        viewModelScope.launch {
            try {
                _workStatus.value = WorkStatus(busy = true, message = "正在写入当前学期…", progress = null)
                val beforeSlots = app.repository.state.value.slots.size
                runCatching {
                    app.repository.commitImportDraft(draftId)
                    NotificationScheduler(app).reschedule(app.repository.state.value)
                    app.repository.state.value.slots.size - beforeSlots
                }.onSuccess { added ->
                    _workStatus.value = WorkStatus(
                        message = if (added > 0) "课表已导入，新增 $added 个时间段并安排提醒" else "课表已核对；重复时间段未再次添加",
                    )
                }.onFailure(::showError)
            } finally {
                timetableImportMutex.unlock()
            }
        }
    }

    fun addCourse(
        name: String,
        teacher: String,
        day: Int,
        start: String,
        end: String,
        room: String,
        startWeek: Int,
        endWeek: Int,
        pattern: WeekPattern,
    ) {
        viewModelScope.launch {
            runCatching {
                require(name.isNotBlank()) { "课程名称不能为空" }
                val termId = appState.value.activeTermId ?: error("请先创建学期")
                val course = Course(termId = termId, name = name.trim(), teacher = teacher.trim())
                val slot = CourseSlot(
                    courseId = course.id,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    room = room.trim(),
                    startWeek = startWeek,
                    endWeek = endWeek,
                    weekPattern = pattern,
                )
                app.repository.upsertCourse(course, listOf(slot))
                NotificationScheduler(app).reschedule(appState.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "课程已添加") }
                .onFailure { showError(it) }
        }
    }

    fun updateCourse(
        course: Course,
        slot: CourseSlot,
        name: String,
        teacher: String,
        day: Int,
        start: String,
        end: String,
        room: String,
        startWeek: Int,
        endWeek: Int,
        pattern: WeekPattern,
        reminderMinutes: Int?,
        liveDisplayName: String,
        notes: String,
    ) {
        viewModelScope.launch {
            runCatching {
                require(name.isNotBlank()) { "课程名称不能为空" }
                require(LocalTime.parse(start) < LocalTime.parse(end)) { "结束时间必须晚于开始时间" }
                require(startWeek in 1..30 && endWeek in startWeek..30) { "周次范围无效" }
                require(reminderMinutes == null || reminderMinutes in 0..180) {
                    "提醒时间应留空，或填写 0 到 180 分钟"
                }
                require(liveDisplayName.length <= 12) { "超级岛简称最多 12 个字符" }
                app.repository.upsertCourse(
                    course.copy(
                        name = name.trim(),
                        teacher = teacher.trim(),
                        reminderOverrideMinutes = reminderMinutes,
                        liveDisplayName = liveDisplayName.trim(),
                        notes = notes.trim(),
                    ),
                    listOf(slot.copy(
                        dayOfWeek = day,
                        startTime = start,
                        endTime = end,
                        room = room.trim(),
                        startWeek = startWeek,
                        endWeek = endWeek,
                        weekPattern = pattern,
                    )),
                )
                NotificationScheduler(app).reschedule(appState.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "课程已更新") }
                .onFailure(::showError)
        }
    }

    fun archiveCourse(courseId: String) {
        viewModelScope.launch {
            runCatching {
                app.repository.archiveCourse(courseId)
                NotificationScheduler(app).reschedule(appState.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "课程已移出当前课表，学习记录仍保留") }
                .onFailure(::showError)
        }
    }

    fun addTerm(name: String, startDate: String, weekCount: Int) {
        viewModelScope.launch {
            runCatching {
                val date = validateTermInput(name, startDate, weekCount)
                app.repository.addTerm(Term(name = name.trim(), startDate = date.toString(), weekCount = weekCount))
                NotificationScheduler(app).reschedule(app.repository.state.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "已创建并切换到新学期") }
                .onFailure(::showError)
        }
    }

    fun updateTerm(termId: String, name: String, startDate: String, weekCount: Int) {
        viewModelScope.launch {
            runCatching {
                val old = appState.value.terms.find { it.id == termId } ?: error("学期不存在")
                val date = validateTermInput(name, startDate, weekCount, termId)
                app.repository.updateTerm(
                    old.copy(name = name.trim(), startDate = date.toString(), weekCount = weekCount),
                )
                NotificationScheduler(app).reschedule(app.repository.state.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "学期信息已保存") }
                .onFailure(::showError)
        }
    }

    private fun validateTermInput(name: String, startDate: String, weekCount: Int, editingId: String? = null): LocalDate {
        require(name.isNotBlank()) { "学期名称不能为空" }
        val date = runCatching { LocalDate.parse(startDate) }.getOrElse {
            error("日期格式应为 YYYY-MM-DD")
        }
        require(date.dayOfWeek == DayOfWeek.MONDAY) { "第一教学周起始日必须是周一" }
        require(weekCount in 1..40) { "教学周数应在 1 到 40 之间" }
        require(appState.value.terms.none {
            it.id != editingId && !it.archived && it.name.equals(name.trim(), ignoreCase = true)
        }) { "已有同名学期" }
        return date
    }

    fun calibrateCurrentTermWeek(currentWeek: Int) {
        viewModelScope.launch {
            runCatching {
                val termId = appState.value.activeTermId ?: error("请先选择学期")
                val term = appState.value.terms.find { it.id == termId } ?: error("当前学期不存在")
                val startDate = AcademicTermPolicy.startDateForCurrentWeek(LocalDate.now(), currentWeek)
                app.repository.updateTerm(term.copy(startDate = startDate.toString()))
                NotificationScheduler(app).reschedule(app.repository.state.value)
                startDate
            }.onSuccess { startDate ->
                _workStatus.value = WorkStatus(message = "已校准：今天是第 $currentWeek 周，学期起始日为 $startDate")
            }.onFailure(::showError)
        }
    }

    fun selectTerm(termId: String) {
        viewModelScope.launch {
            runCatching {
                app.repository.setActiveTerm(termId)
                NotificationScheduler(app).reschedule(app.repository.state.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "已切换当前学期") }
                .onFailure(::showError)
        }
    }

    fun archiveTerm(termId: String) {
        viewModelScope.launch {
            runCatching {
                app.repository.archiveTerm(termId)
                NotificationScheduler(app).reschedule(app.repository.state.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "学期已归档，课程和记录仍保留") }
                .onFailure(::showError)
        }
    }

    fun restoreTerm(termId: String) {
        viewModelScope.launch {
            runCatching {
                app.repository.restoreTerm(termId)
                NotificationScheduler(app).reschedule(app.repository.state.value)
            }.onSuccess { _workStatus.value = WorkStatus(message = "已恢复并切换到该学期") }
                .onFailure(::showError)
        }
    }

    fun addProject(name: String, description: String) {
        viewModelScope.launch {
            runCatching {
                require(name.isNotBlank()) { "项目名称不能为空" }
                app.repository.addProject(StudyProject(name = name.trim(), description = description.trim()))
            }.onSuccess { _workStatus.value = WorkStatus(message = "自学项目已创建") }
                .onFailure { showError(it) }
        }
    }

    fun updateProjectProgress(project: StudyProject, progress: String) {
        viewModelScope.launch {
            app.repository.updateProject(project.copy(currentProgress = progress.trim()))
        }
    }

    fun addMaterial(ownerId: String, ownerType: String, uri: Uri) {
        viewModelScope.launch {
            _workStatus.value = WorkStatus(busy = true, message = "正在登记资料…")
            runCatching {
                val document = DocumentFile.fromSingleUri(app, uri)
                val title = document?.name ?: "未命名资料"
                val checksum = app.contentResolver.openInputStream(uri)?.use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }.orEmpty()
                app.repository.addMaterial(
                    MaterialItem(
                        ownerId = ownerId,
                        ownerType = ownerType,
                        title = title,
                        uri = uri.toString(),
                        kind = inferMaterialKind(document?.type, title),
                        checksum = checksum,
                        addedAt = OffsetDateTime.now().toString(),
                    ),
                )
            }.onSuccess { _workStatus.value = WorkStatus(message = "资料已归档") }
                .onFailure { showError(it) }
        }
    }

    private fun inferMaterialKind(mime: String?, title: String): MaterialKind = when {
        mime == "application/pdf" -> MaterialKind.DOCUMENT
        mime?.startsWith("image/") == true -> MaterialKind.IMAGE
        mime?.startsWith("video/") == true -> MaterialKind.VIDEO
        mime?.startsWith("audio/") == true -> MaterialKind.AUDIO
        title.endsWith(".v", true) || title.endsWith(".sv", true) || title.endsWith(".vhd", true) -> MaterialKind.CODE
        else -> MaterialKind.OTHER
    }

    fun startSession(ownerId: String, ownerType: String, title: String) {
        appState.value.sessions.lastOrNull {
            it.ownerId == ownerId && it.ownerType == ownerType && it.endedAt == null
        }?.let {
            _activeSession.value = it
            _workStatus.value = WorkStatus(message = "已继续未完成的记录")
            return
        }
        viewModelScope.launch {
            runCatching { app.repository.startSession(ownerId, ownerType, title) }
                .onSuccess {
                    _activeSession.value = it
                    _workStatus.value = WorkStatus(message = "已开始记录；可随时追加痛点或课堂原文")
                }
                .onFailure { showError(it) }
        }
    }

    fun dismissActiveSession() {
        _activeSession.value = null
    }

    fun appendEvent(kind: LearningEventKind, content: String) {
        val session = _activeSession.value ?: return
        viewModelScope.launch {
            if (content.isNotBlank()) app.repository.appendEvent(session.id, kind, content.trim())
        }
    }

    fun finishSession(summary: String, transcript: String, complete: Boolean) {
        val session = _activeSession.value ?: return
        viewModelScope.launch {
            runCatching { app.repository.finishSession(session.id, summary, transcript, complete) }
                .onSuccess {
                    _activeSession.value = null
                    _workStatus.value = WorkStatus(message = "本节记录已写入本地 Git 历史")
                }
                .onFailure { showError(it) }
        }
    }

    fun importLearningSessionJson(text: String, finish: Boolean) {
        val session = _activeSession.value ?: return
        viewModelScope.launch {
            runCatching {
                val payload = LearningSessionPayloadParser.parse(text)
                app.repository.importLearningSessionPayload(session.id, payload, finish)
                payload
            }.onSuccess { payload ->
                if (finish) {
                    _activeSession.value = null
                } else {
                    // Repository state is updated synchronously before persistence finishes.
                    // Reading it directly avoids briefly restoring the pre-import draft from
                    // the asynchronously collected UI state.
                    _activeSession.value = app.repository.state.value.sessions.find { it.id == session.id }
                }
                _workStatus.value = WorkStatus(
                    message = if (finish) {
                        "已导入 ${payload.events.size} 条事件，并保存本节记录"
                    } else {
                        "已导入 ${payload.events.size} 条事件，可继续记录"
                    },
                )
            }.onFailure(::showError)
        }
    }

    fun setNextEarlyAlarm() {
        val result = EarlyAlarmService(app).setNextEarlyClassAlarm(appState.value)
        _workStatus.value = result.fold(
            onSuccess = { WorkStatus(message = it) },
            onFailure = { WorkStatus(message = "闹钟设置失败：${it.message}") },
        )
    }

    fun ownerName(ownerId: String): String = appState.value.courses.find { it.id == ownerId }?.name
        ?: appState.value.projects.find { it.id == ownerId }?.name
        ?: "未知分类"

    fun todayClasses() = ScheduleEngine.classesOn(appState.value, LocalDate.now())

    private fun showError(error: Throwable) {
        _workStatus.value = WorkStatus(message = error.message ?: "操作失败")
    }
}
