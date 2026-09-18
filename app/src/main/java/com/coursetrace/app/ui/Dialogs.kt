@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.coursetrace.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.coursetrace.app.domain.AcademicTermPolicy
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import com.coursetrace.app.model.LearningEvent
import com.coursetrace.app.model.LearningEventKind
import com.coursetrace.app.model.LearningSession
import com.coursetrace.app.model.Term
import com.coursetrace.app.model.WeekPattern
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String, String, String, Int, Int, WeekPattern) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(1) }
    var start by remember { mutableStateOf("08:50") }
    var end by remember { mutableStateOf("10:25") }
    var firstWeek by remember { mutableStateOf("1") }
    var lastWeek by remember { mutableStateOf("20") }
    var pattern by remember { mutableStateOf(WeekPattern.EVERY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(room, { room = it }, label = { Text("教室") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { value ->
                        FilterChip(selected = day == value, onClick = { day = value }, label = { Text(ScheduleEngine.dayLabel(value)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("开始 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("结束 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(firstWeek, { firstWeek = it.filter(Char::isDigit) }, label = { Text("起始周") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(lastWeek, { lastWeek = it.filter(Char::isDigit) }, label = { Text("结束周") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekPattern.entries.forEach { value ->
                        FilterChip(
                            selected = pattern == value,
                            onClick = { pattern = value },
                            label = { Text(when (value) { WeekPattern.EVERY -> "每周"; WeekPattern.ODD -> "单周"; WeekPattern.EVEN -> "双周" }) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        teacher,
                        day,
                        start,
                        end,
                        room,
                        firstWeek.toIntOrNull() ?: 1,
                        lastWeek.toIntOrNull() ?: 20,
                        pattern,
                    )
                },
                enabled = name.isNotBlank() && Regex("\\d{2}:\\d{2}").matches(start) && Regex("\\d{2}:\\d{2}").matches(end),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun EditCourseDialog(
    course: Course,
    slot: CourseSlot,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String, String, String, Int, Int, WeekPattern, Int?, String, String) -> Unit,
) {
    var name by remember(course.id) { mutableStateOf(course.name) }
    var teacher by remember(course.id) { mutableStateOf(course.teacher) }
    var room by remember(slot.id) { mutableStateOf(slot.room) }
    var day by remember(slot.id) { mutableIntStateOf(slot.dayOfWeek) }
    var start by remember(slot.id) { mutableStateOf(slot.startTime) }
    var end by remember(slot.id) { mutableStateOf(slot.endTime) }
    var firstWeek by remember(slot.id) { mutableStateOf(slot.startWeek.toString()) }
    var lastWeek by remember(slot.id) { mutableStateOf(slot.endWeek.toString()) }
    var pattern by remember(slot.id) { mutableStateOf(slot.weekPattern) }
    var reminder by remember(course.id) { mutableStateOf(course.reminderOverrideMinutes?.toString().orEmpty()) }
    var liveDisplayName by remember(course.id) { mutableStateOf(course.liveDisplayName) }
    var notes by remember(course.id) { mutableStateOf(course.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑课程") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(room, { room = it }, label = { Text("教室") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { value ->
                        FilterChip(selected = day == value, onClick = { day = value }, label = { Text(ScheduleEngine.dayLabel(value)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("开始 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("结束 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(firstWeek, { firstWeek = it.filter(Char::isDigit) }, label = { Text("起始周") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(lastWeek, { lastWeek = it.filter(Char::isDigit) }, label = { Text("结束周") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekPattern.entries.forEach { value ->
                        FilterChip(
                            selected = pattern == value,
                            onClick = { pattern = value },
                            label = { Text(when (value) { WeekPattern.EVERY -> "每周"; WeekPattern.ODD -> "单周"; WeekPattern.EVEN -> "双周" }) },
                        )
                    }
                }
                OutlinedTextField(
                    reminder,
                    { reminder = it.filter(Char::isDigit) },
                    label = { Text("单独提醒分钟数") },
                    supportingText = { Text("留空跟随设置；0 关闭这门课的提醒") },
                    singleLine = true,
                )
                OutlinedTextField(
                    liveDisplayName,
                    { liveDisplayName = it.take(12) },
                    label = { Text("超级岛简称") },
                    supportingText = { Text("建议 2–6 字；留空时自动精简课程名") },
                    singleLine = true,
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("课程备注") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name, teacher, day, start, end, room,
                        firstWeek.toIntOrNull() ?: 1,
                        lastWeek.toIntOrNull() ?: 20,
                        pattern,
                        reminder.toIntOrNull(),
                        liveDisplayName,
                        notes,
                    )
                },
                enabled = name.isNotBlank() && Regex("\\d{2}:\\d{2}").matches(start) &&
                    Regex("\\d{2}:\\d{2}").matches(end),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun AddProjectDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自学项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称，例如 FPGA") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("目标或说明") }, minLines = 3)
                Text("自学项目不会触发上课提醒，但同样支持资料、原文、痛点和 ChatGPT 分析。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, description) }, enabled = name.isNotBlank()) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun BackupPasswordDialog(restore: Boolean = false, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (restore) "输入备份恢复口令" else "设置备份恢复口令") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("至少 12 个字符") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (!restore) {
                    OutlinedTextField(
                        confirmation,
                        { confirmation = it },
                        label = { Text("再次输入") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                Text(
                    "口令不会上传或保存。丢失后无法恢复备份；可以在系统文件选择器中直接选择网盘或 WebDAV 文件提供器。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.length >= 12 && (restore || password == confirmation),
            ) { Text(if (restore) "选择备份并恢复" else "选择保存位置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ManageTermsDialog(
    terms: List<Term>,
    activeTermId: String?,
    courseCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: (String, String, Int) -> Unit,
    onUpdate: (String, String, String, Int) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onCalibrateCurrentWeek: (Int) -> Unit,
) {
    val activeTerm = terms.find { it.id == activeTermId }
    val today = LocalDate.now()
    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val editingTerm = terms.find { it.id == editingId }
    val suggestedStart = remember(terms, activeTermId) {
        activeTerm?.let { term ->
            runCatching { LocalDate.parse(term.startDate).plusWeeks(term.weekCount.toLong()) }.getOrNull()
        } ?: today.minusDays((today.dayOfWeek.value - 1).toLong())
    }
    var editorName by remember(creating, editingId) {
        mutableStateOf(
            if (creating) AcademicTermPolicy.suggestedName(suggestedStart) else editingTerm?.name.orEmpty(),
        )
    }
    var editorStartDate by remember(creating, editingId) {
        mutableStateOf(if (creating) suggestedStart.toString() else editingTerm?.startDate.orEmpty())
    }
    var editorWeeks by remember(creating, editingId) {
        mutableStateOf(if (creating) "20" else editingTerm?.weekCount?.toString().orEmpty())
    }
    val parsedEditorDate = runCatching { LocalDate.parse(editorStartDate) }.getOrNull()
    val parsedEditorWeeks = editorWeeks.toIntOrNull()
    val duplicateName = terms.any {
        it.id != editingId && !it.archived && it.name.equals(editorName.trim(), ignoreCase = true)
    }
    val editorError = when {
        editorName.isBlank() -> "请输入学期名称"
        duplicateName -> "已有同名学期"
        parsedEditorDate == null -> "日期格式应为 YYYY-MM-DD"
        parsedEditorDate.dayOfWeek.value != 1 -> "第一教学周起始日必须是周一"
        parsedEditorWeeks == null || parsedEditorWeeks !in 1..40 -> "教学周数应在 1 到 40 之间"
        else -> null
    }

    if (creating || editingTerm != null) {
        AlertDialog(
            onDismissRequest = {
                creating = false
                editingId = null
            },
            title = { Text(if (creating) "新建学期" else "编辑学期") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "开学日期指第一教学周的周一，用它计算所有课程所属周次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = editorName,
                        onValueChange = { editorName = it },
                        label = { Text("学期名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editorStartDate,
                        onValueChange = { editorStartDate = it.trim() },
                        label = { Text("第一教学周的周一") },
                        supportingText = { Text("格式：YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editorWeeks,
                        onValueChange = { editorWeeks = it.filter(Char::isDigit) },
                        label = { Text("教学周数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (editorError != null) {
                        Text(editorError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!creating && editingTerm != null) {
                        Text(
                            "修改周次锚点不会删除课程或记录，但会改变课程对应的日历日期。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = editorError == null,
                    onClick = {
                        val weekCount = parsedEditorWeeks ?: return@Button
                        if (creating) {
                            onAdd(editorName, editorStartDate, weekCount)
                        } else {
                            onUpdate(requireNotNull(editingId), editorName, editorStartDate, weekCount)
                        }
                        creating = false
                        editingId = null
                    },
                ) { Text(if (creating) "创建并切换" else "保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    creating = false
                    editingId = null
                }) { Text("返回") }
            },
        )
        return
    }

    var currentWeek by remember(activeTermId, activeTerm?.startDate) {
        mutableStateOf(
            activeTerm?.let { ScheduleEngine.weekNumber(it, today).coerceIn(1, it.weekCount).toString() } ?: "1",
        )
    }
    var showCalibration by remember(activeTermId) { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var confirmArchive by remember(activeTermId) { mutableStateOf(false) }
    val usableTerms = terms.filterNot { it.archived }
    val archivedTerms = terms.filter { it.archived }
    val otherTerms = usableTerms.filterNot { it.id == activeTermId }
    val requestedWeek = currentWeek.toIntOrNull()
    val calibratedStart = requestedWeek?.takeIf { it in 1..40 }?.let {
        AcademicTermPolicy.startDateForCurrentWeek(today, it)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "课程表、提醒和桌面组件只使用当前学期。切换不会删除任何数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeTerm != null) {
                    val week = ScheduleEngine.weekNumber(activeTerm, today)
                    val timing = when {
                        week < 1 -> "尚未开始 · 共 ${activeTerm.weekCount} 周"
                        week > activeTerm.weekCount -> "已结束 · 共 ${activeTerm.weekCount} 周"
                        else -> "第 $week 周 / 共 ${activeTerm.weekCount} 周"
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("当前学期", style = MaterialTheme.typography.labelMedium)
                            Text(activeTerm.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(timing)
                            Text(
                                "第一周周一 ${activeTerm.startDate} · ${courseCounts[activeTerm.id] ?: 0} 门课程",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editingId = activeTerm.id }) { Text("编辑信息") }
                                TextButton(onClick = { showCalibration = !showCalibration }) {
                                    Text(if (showCalibration) "收起校准" else "校准教学周")
                                }
                            }
                        }
                    }
                    AnimatedVisibility(showCalibration) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("以今天为准校准", fontWeight = FontWeight.SemiBold)
                            Text(
                                "只调整第一周周一，不改变课程填写的上课周次。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = currentWeek,
                                    onValueChange = { currentWeek = it.filter(Char::isDigit) },
                                    label = { Text("今天是第几周") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                Button(
                                    onClick = {
                                        requestedWeek?.let(onCalibrateCurrentWeek)
                                        showCalibration = false
                                    },
                                    enabled = calibratedStart != null,
                                ) { Text("确认") }
                            }
                            calibratedStart?.let {
                                Text(
                                    "校准后：第一周周一为 $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                } else {
                    Text("尚未选择当前学期", color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider()
                Text("切换学期", fontWeight = FontWeight.SemiBold)
                if (otherTerms.isEmpty()) {
                    Text(
                        "暂无其他使用中的学期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                otherTerms.sortedByDescending { it.startDate }.forEach { term ->
                    ListItem(
                        headlineContent = { Text(term.name) },
                        supportingContent = {
                            Text("${term.startDate} · ${term.weekCount} 周 · ${courseCounts[term.id] ?: 0} 门课程")
                        },
                        leadingContent = { RadioButton(selected = false, onClick = null) },
                        trailingContent = { TextButton(onClick = { onSelect(term.id) }) { Text("切换") } },
                        modifier = Modifier.clickable { onSelect(term.id) },
                    )
                }
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("新建学期")
                }

                if (archivedTerms.isNotEmpty()) {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Icon(Icons.Outlined.Restore, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (showArchived) "收起已归档" else "已归档（${archivedTerms.size}）")
                    }
                    AnimatedVisibility(showArchived) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            archivedTerms.sortedByDescending { it.startDate }.forEach { term ->
                                ListItem(
                                    headlineContent = { Text(term.name) },
                                    supportingContent = { Text("${term.startDate} · ${courseCounts[term.id] ?: 0} 门课程") },
                                    trailingContent = {
                                        TextButton(onClick = { onRestore(term.id) }) { Text("恢复并切换") }
                                    },
                                )
                            }
                        }
                    }
                }

                if (activeTerm != null && usableTerms.size > 1) {
                    HorizontalDivider()
                    if (!confirmArchive) {
                        TextButton(onClick = { confirmArchive = true }, modifier = Modifier.align(Alignment.End)) {
                            Text("归档当前学期")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("确认归档“${activeTerm.name}”？", fontWeight = FontWeight.SemiBold)
                                Text("课程和学习记录会保留，可随时从已归档中恢复。", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.align(Alignment.End)) {
                                    TextButton(onClick = { confirmArchive = false }) { Text("取消") }
                                    TextButton(onClick = {
                                        onArchive(activeTerm.id)
                                        confirmArchive = false
                                    }) { Text("确认归档") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
fun OwnerDetailSheet(
    state: AppState,
    ownerId: String,
    ownerType: String,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onAddMaterial: () -> Unit,
    onStartSession: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val materials = state.materials.filter { it.ownerId == ownerId }
    val sessions = state.sessions.filter { it.ownerId == ownerId }.sortedByDescending { it.startedAt }
    val prediction = state.predictions.find { it.ownerId == ownerId }
    var selectedSessionId by remember(ownerId) { mutableStateOf<String?>(null) }
    val selectedSession = sessions.find { it.id == selectedSessionId }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (selectedSession != null) {
            LearningRecordDetail(
                session = selectedSession,
                events = state.events.filter { it.sessionId == selectedSession.id }.sortedBy { it.sequence },
                onBack = { selectedSessionId = null },
                onDismiss = onDismiss,
            )
        } else LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 0.dp, 20.dp, 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartSession, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        val continuing = sessions.any { it.endedAt == null }
                        Text(
                            when {
                                continuing && ownerType == "course" -> "继续课堂记录"
                                continuing -> "继续学习"
                                ownerType == "course" -> "开始课堂记录"
                                else -> "开始学习"
                            },
                        )
                    }
                    OutlinedButton(onClick = onAddMaterial) { Icon(Icons.Outlined.AttachFile, "添加资料") }
                }
                if (ownerType == "course" && onEdit != null && onArchive != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Edit, null)
                            Spacer(Modifier.width(6.dp))
                            Text("编辑课程")
                        }
                        TextButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Archive, null)
                            Spacer(Modifier.width(6.dp))
                            Text("移出课表")
                        }
                    }
                }
            }
            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("下次资料预测", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (prediction == null) {
                        Text("完成一次 ChatGPT 课堂记录后，会依据上次进度给出带置信度的预测；不会虚构不存在的文件。")
                    } else {
                        Text(prediction.title, fontWeight = FontWeight.SemiBold)
                        Text(prediction.reason)
                        Text("置信度 ${(prediction.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onAddMaterial) { Icon(Icons.Filled.Add, null); Text("添加") }
                }
            }
            if (materials.isEmpty()) {
                item { Text("还没有资料", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(materials, key = { it.id }) { material ->
                    ListItem(
                        headlineContent = { Text(material.title) },
                        supportingContent = { Text(material.kind.name.lowercase()) },
                        leadingContent = { Icon(Icons.Outlined.FolderOpen, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, null)
                    Spacer(Modifier.width(8.dp))
                    Text("最近学习记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (sessions.isEmpty()) item { Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(sessions.take(8), key = { it.id }) { session ->
                ListItem(
                    headlineContent = { Text(session.title) },
                    supportingContent = {
                        Column {
                            Text(session.summary.ifBlank { if (session.endedAt == null) "进行中" else "未填写摘要" })
                            Text(formatRecordTime(session.startedAt), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, "查看记录") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSessionId = session.id },
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        val prompt = buildString {
                            append("我正在学习《$title》。请使用课迹 CourseTrace 工具识别当前课程/项目，读取最近进度并开始记录。")
                            if (prediction != null) append(" 当前预测：${prediction.title}（置信度 ${(prediction.confidence * 100).toInt()}%）。")
                            append(" 当我说下课时，请仅输出可导入课迹的 JSON，字段为 summary、events[{kind,content,occurredAt}]、rawTranscript、transcriptComplete；kind 使用 QUESTION、PAIN_POINT、WRONG_ANSWER、PROGRESS、DECISION、NOTE 或 TRANSCRIPT。")
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, prompt)
                        }, "发送到 ChatGPT"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null)
                    Spacer(Modifier.width(8.dp))
                    Text("在 ChatGPT 中继续")
                }
            }
            }
    }
}

@Composable
private fun LearningRecordDetail(
    session: LearningSession,
    events: List<LearningEvent>,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 0.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回课程") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }
            }
            Text(session.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    append(formatRecordTime(session.startedAt))
                    session.endedAt?.let { append(" – ${formatRecordTime(it)}") } ?: append(" · 仍在进行")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SoftCard {
                Text("本节摘要", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(session.summary.ifBlank { "这条记录还没有摘要。" })
            }
        }
        item {
            Text("事件时间线 · ${events.size} 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (events.isEmpty()) {
            item { Text("没有单独记录的事件。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else items(events, key = { it.id }) { event ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(learningEventLabel(event.kind), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(formatRecordTime(event.timestamp), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(event.content)
                if (event.source != "local") {
                    Text("来源：ChatGPT 导入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("课堂原文", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (session.transcriptComplete) "已标记为完整" else "未标记为完整",
                style = MaterialTheme.typography.labelMedium,
                color = if (session.transcriptComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SoftCard {
                Text(session.rawTranscript.ifBlank { "没有保存课堂原文。" })
            }
        }
    }
}

private fun learningEventLabel(kind: LearningEventKind): String = when (kind) {
    LearningEventKind.TRANSCRIPT -> "原文"
    LearningEventKind.QUESTION -> "问题"
    LearningEventKind.PAIN_POINT -> "痛点"
    LearningEventKind.WRONG_ANSWER -> "错题"
    LearningEventKind.PROGRESS -> "进度"
    LearningEventKind.DECISION -> "结论"
    LearningEventKind.NOTE -> "笔记"
}

private fun formatRecordTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))
}.getOrElse { value.take(16).replace('T', ' ') }

@Composable
fun ActiveSessionSheet(
    session: LearningSession,
    onDismiss: () -> Unit,
    onAppend: (LearningEventKind, String) -> Unit,
    onFinish: (String, String, Boolean) -> Unit,
    onImportJson: (String, Boolean) -> Unit,
) {
    var kind by remember(session.id) { mutableStateOf(LearningEventKind.NOTE) }
    var eventText by remember(session.id) { mutableStateOf("") }
    var transcript by remember(session.id, session.rawTranscript) { mutableStateOf(session.rawTranscript) }
    var summary by remember(session.id, session.summary) { mutableStateOf(session.summary) }
    var complete by remember(session.id, session.transcriptComplete) { mutableStateOf(session.transcriptComplete) }
    var finishing by remember(session.id) { mutableStateOf(false) }
    var showJsonImport by remember(session.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("正在按事件顺序记录", color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "关闭记录页")
                }
            }
            SoftCard {
                Text("记录方式", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("• 自己随手记：在下方选类型并追加到时间线。")
                Text("• ChatGPT 返回 JSON：使用专用导入入口，不要粘到过程或摘要框。")
                Text("• 普通聊天全文：只粘到“结束并整理”里的课堂原文框。")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showJsonImport = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null)
                    Spacer(Modifier.width(8.dp))
                    Text("粘贴 ChatGPT JSON")
                }
            }
            if (!finishing) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LearningEventKind.NOTE to "笔记",
                        LearningEventKind.QUESTION to "问题",
                        LearningEventKind.PAIN_POINT to "痛点",
                        LearningEventKind.WRONG_ANSWER to "错题",
                        LearningEventKind.PROGRESS to "进度",
                    ).forEach { (value, label) ->
                        FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = eventText,
                    onValueChange = { eventText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("手动记录刚刚发生的内容") },
                    supportingText = { Text("这里只写一条笔记、问题、痛点或进度，不粘贴整段 JSON") },
                    minLines = 3,
                )
                Button(
                    onClick = { onAppend(kind, eventText); eventText = "" },
                    enabled = eventText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("追加到时间线") }
                TextButton(onClick = { finishing = true }, modifier = Modifier.align(Alignment.End)) { Text("结束并整理") }
            } else {
                OutlinedTextField(
                    summary,
                    { summary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本节摘要") },
                    supportingText = { Text("填写便于回顾的人类可读总结，不粘贴 JSON") },
                    minLines = 2,
                )
                OutlinedTextField(
                    transcript,
                    { transcript = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("完整聊天原文") },
                    supportingText = { Text("这里粘贴普通聊天全文；结构化 JSON 请使用上方专用入口") },
                    minLines = 5,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(complete, { complete = it })
                    Text("这份原文是完整的")
                }
                Text(
                    "课迹不会声称被动获取了完整 ChatGPT 聊天；未勾选时会保留“不完整”标记。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { finishing = false }, modifier = Modifier.weight(1f)) { Text("返回记录") }
                    Button(onClick = { onFinish(summary, transcript, complete) }, modifier = Modifier.weight(1f)) { Text("保存并结束") }
                }
            }
        }
    }
    if (showJsonImport) {
        LearningJsonImportDialog(
            onDismiss = { showJsonImport = false },
            onImport = { text, finish ->
                onImportJson(text, finish)
                showJsonImport = false
            },
        )
    }
}

@Composable
private fun LearningJsonImportDialog(
    onDismiss: () -> Unit,
    onImport: (String, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 ChatGPT 课堂 JSON") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("把 ChatGPT 返回的整个 JSON 对象粘贴到这里。课迹会解析摘要、事件时间线、原文和完整性标记。")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("课堂记录 JSON") },
                    placeholder = { Text("{\"summary\":\"…\",\"events\":[…]}") },
                    minLines = 8,
                )
                OutlinedButton(
                    onClick = { onImport(text, false) },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导入并继续记录") }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(text, true) }, enabled = text.isNotBlank()) {
                Text("导入并结束本节")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
