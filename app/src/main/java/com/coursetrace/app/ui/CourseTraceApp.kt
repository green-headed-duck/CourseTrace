package com.coursetrace.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coursetrace.app.MainViewModel
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.StudyProject
import java.time.LocalDate

enum class MainSection(val label: String, val icon: ImageVector) {
    TODAY("今天", Icons.Outlined.Home),
    SCHEDULE("课表", Icons.Outlined.CalendarMonth),
    PROJECTS("自学", Icons.Outlined.AutoStories),
    MATERIALS("资料", Icons.Outlined.FolderOpen),
    SETTINGS("设置", Icons.Outlined.Settings),
}

data class MaterialOwner(val id: String, val type: String, val name: String)

@Composable
fun CourseTraceApp(viewModel: MainViewModel) {
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val workStatus by viewModel.workStatus.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val openCourseId by viewModel.openCourseId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var section by rememberSaveable { mutableStateOf(MainSection.TODAY) }
    var showAddCourse by rememberSaveable { mutableStateOf(false) }
    var showAddProject by rememberSaveable { mutableStateOf(false) }
    var showTermManager by rememberSaveable { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var courseToArchive by remember { mutableStateOf<Course?>(null) }
    var selectedProject by remember { mutableStateOf<StudyProject?>(null) }
    var pendingMaterialOwner by remember { mutableStateOf<MaterialOwner?>(null) }
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<CharArray?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.importPdf(it)
        }
    }
    val materialPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val owner = pendingMaterialOwner
        if (uri != null && owner != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.addMaterial(owner.id, owner.type, uri)
        }
        pendingMaterialOwner = null
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingBackupPassword
        if (uri != null && password != null) viewModel.exportEncryptedBackup(uri, password)
        else password?.fill('\u0000')
        pendingBackupPassword = null
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingBackupPassword
        if (uri != null && password != null) viewModel.restoreEncryptedBackup(uri, password)
        else password?.fill('\u0000')
        pendingBackupPassword = null
    }

    LaunchedEffect(workStatus.message) {
        workStatus.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(openCourseId, appState.courses) {
        openCourseId?.let { id ->
            appState.courses.find { it.id == id }?.let {
                selectedCourse = it
                section = MainSection.SCHEDULE
            }
            viewModel.consumeOpenCourse()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        MainSection.entries.forEach { item ->
                            NavigationBarItem(
                                selected = section == item,
                                onClick = { section = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        MainSection.entries.forEach { item ->
                            NavigationRailItem(
                                selected = section == item,
                                onClick = { section = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    AnimatedContent(
                        targetState = section,
                        label = "main-section",
                        transitionSpec = {
                            if (appState.preferences.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                            else fadeIn() togetherWith fadeOut()
                        },
                    ) { target ->
                        when (target) {
                            MainSection.TODAY -> TodayScreen(
                                state = appState,
                                activeSession = activeSession,
                                onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                                onAddCourse = { showAddCourse = true },
                                onCourseClick = { selectedCourse = it },
                                onStartSession = viewModel::startSession,
                            )
                            MainSection.SCHEDULE -> ScheduleScreen(
                                state = appState,
                                onAddCourse = { showAddCourse = true },
                                onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                                onCourseClick = { selectedCourse = it },
                                onCommitImport = viewModel::commitImport,
                                onManageTerms = { showTermManager = true },
                            )
                            MainSection.PROJECTS -> ProjectsScreen(
                                state = appState,
                                onAddProject = { showAddProject = true },
                                onProjectClick = { selectedProject = it },
                                onStartSession = viewModel::startSession,
                            )
                            MainSection.MATERIALS -> MaterialsScreen(
                                state = appState,
                                ownerName = viewModel::ownerName,
                                onAddMaterial = { owner ->
                                    pendingMaterialOwner = owner
                                    materialPicker.launch(arrayOf("*/*"))
                                },
                            )
                            MainSection.SETTINGS -> SettingsScreen(
                                state = appState,
                                hasApiKey = viewModel.hasApiKey(),
                                hasRelayToken = viewModel.hasRelayToken(),
                                busy = workStatus.busy,
                                onUpdatePreferences = viewModel::updatePreferences,
                                onSaveApi = viewModel::saveApiProfile,
                                onTestApi = viewModel::testApi,
                                onSetEarlyAlarm = viewModel::setNextEarlyAlarm,
                                onSaveChatGptLink = viewModel::saveChatGptLink,
                                onSyncChatGpt = viewModel::syncChatGptNow,
                                onCheckUpdate = viewModel::checkForUpdates,
                                onExportBackup = { showBackupDialog = true },
                                onRestoreBackup = { showRestoreDialog = true },
                            )
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(showAddCourse) {
        AddCourseDialog(
            onDismiss = { showAddCourse = false },
            onConfirm = { name, teacher, day, start, end, room, firstWeek, lastWeek, pattern ->
                viewModel.addCourse(name, teacher, day, start, end, room, firstWeek, lastWeek, pattern)
                showAddCourse = false
            },
        )
    }
    AnimatedVisibility(showAddProject) {
        AddProjectDialog(
            onDismiss = { showAddProject = false },
            onConfirm = { name, description ->
                viewModel.addProject(name, description)
                showAddProject = false
            },
        )
    }
    if (showTermManager) {
        ManageTermsDialog(
            terms = appState.terms,
            activeTermId = appState.activeTermId,
            onDismiss = { showTermManager = false },
            onSelect = viewModel::selectTerm,
            onAdd = viewModel::addTerm,
            onArchive = viewModel::archiveTerm,
        )
    }
    selectedCourse?.takeIf { activeSession == null }?.let { course ->
        OwnerDetailSheet(
            state = appState,
            ownerId = course.id,
            ownerType = "course",
            title = course.name,
            subtitle = listOf(course.teacher, course.notes).filter(String::isNotBlank).joinToString(" · "),
            onDismiss = { selectedCourse = null },
            onAddMaterial = {
                pendingMaterialOwner = MaterialOwner(course.id, "course", course.name)
                materialPicker.launch(arrayOf("*/*"))
            },
            onStartSession = { viewModel.startSession(course.id, "course", course.name) },
            onEdit = {
                editingCourse = course
                selectedCourse = null
            },
            onArchive = {
                courseToArchive = course
                selectedCourse = null
            },
        )
    }
    editingCourse?.let { course ->
        appState.slots.firstOrNull { it.courseId == course.id }?.let { slot ->
            EditCourseDialog(
                course = course,
                slot = slot,
                onDismiss = { editingCourse = null },
                onConfirm = { name, teacher, day, start, end, room, firstWeek, lastWeek, pattern, reminder, liveDisplayName, notes ->
                    viewModel.updateCourse(
                        course, slot, name, teacher, day, start, end, room,
                        firstWeek, lastWeek, pattern, reminder, liveDisplayName, notes,
                    )
                    editingCourse = null
                },
            )
        }
    }
    courseToArchive?.let { course ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { courseToArchive = null },
            title = { Text("将课程移出课表？") },
            text = { Text("${course.name} 将不再显示或提醒，但课堂记录和资料会继续保留。") },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    viewModel.archiveCourse(course.id)
                    courseToArchive = null
                }) { Text("确认移出") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { courseToArchive = null }) { Text("取消") }
            },
        )
    }
    selectedProject?.takeIf { activeSession == null }?.let { project ->
        OwnerDetailSheet(
            state = appState,
            ownerId = project.id,
            ownerType = "project",
            title = project.name,
            subtitle = project.description,
            onDismiss = { selectedProject = null },
            onAddMaterial = {
                pendingMaterialOwner = MaterialOwner(project.id, "project", project.name)
                materialPicker.launch(arrayOf("*/*"))
            },
            onStartSession = { viewModel.startSession(project.id, "project", project.name) },
        )
    }
    activeSession?.let {
        ActiveSessionSheet(
            session = it,
            onDismiss = viewModel::dismissActiveSession,
            onAppend = viewModel::appendEvent,
            onFinish = viewModel::finishSession,
        )
    }
    if (showBackupDialog) {
        BackupPasswordDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { password ->
                pendingBackupPassword = password.toCharArray()
                showBackupDialog = false
                backupPicker.launch("coursetrace-backup-${LocalDate.now()}.ctrace")
            },
        )
    }
    if (showRestoreDialog) {
        BackupPasswordDialog(
            restore = true,
            onDismiss = { showRestoreDialog = false },
            onConfirm = { password ->
                pendingBackupPassword = password.toCharArray()
                showRestoreDialog = false
                restorePicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
            },
        )
    }
}
