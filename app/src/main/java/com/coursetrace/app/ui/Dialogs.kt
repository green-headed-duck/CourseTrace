@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.coursetrace.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.CourseSlot
import com.coursetrace.app.model.LearningEventKind
import com.coursetrace.app.model.LearningSession
import com.coursetrace.app.model.Term
import com.coursetrace.app.model.WeekPattern

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
    onConfirm: (String, String, Int, String, String, String, Int, Int, WeekPattern, Int?, String) -> Unit,
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
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: (String, String, Int) -> Unit,
    onArchive: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var weeks by remember { mutableStateOf("20") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    terms.filterNot { it.archived }.forEach { term ->
                        FilterChip(
                            selected = term.id == activeTermId,
                            onClick = { onSelect(term.id) },
                            label = { Text(term.name) },
                        )
                    }
                }
                HorizontalDivider()
                Text("新学期", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startDate, { startDate = it }, label = { Text("开学日期 YYYY-MM-DD") }, modifier = Modifier.weight(2f), singleLine = true)
                    OutlinedTextField(weeks, { weeks = it.filter(Char::isDigit) }, label = { Text("周数") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Button(
                    onClick = { onAdd(name, startDate, weeks.toIntOrNull() ?: 20); name = "" },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("创建并切换") }
                if (activeTermId != null && terms.count { !it.archived } > 1) {
                    TextButton(onClick = { onArchive(activeTermId) }, modifier = Modifier.align(Alignment.End)) {
                        Text("归档当前学期")
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
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
                    supportingContent = { Text(session.summary.ifBlank { if (session.endedAt == null) "进行中" else "未填写摘要" }) },
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        val prompt = buildString {
                            append("我正在学习《$title》。请使用课迹 CourseTrace 工具识别当前课程/项目，读取最近进度并开始记录。")
                            if (prediction != null) append(" 当前预测：${prediction.title}（置信度 ${(prediction.confidence * 100).toInt()}%）。")
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
fun ActiveSessionSheet(
    session: LearningSession,
    onDismiss: () -> Unit,
    onAppend: (LearningEventKind, String) -> Unit,
    onFinish: (String, String, Boolean) -> Unit,
) {
    var kind by remember(session.id) { mutableStateOf(LearningEventKind.NOTE) }
    var eventText by remember(session.id) { mutableStateOf("") }
    var transcript by remember(session.id) { mutableStateOf("") }
    var summary by remember(session.id) { mutableStateOf("") }
    var complete by remember(session.id) { mutableStateOf(false) }
    var finishing by remember(session.id) { mutableStateOf(false) }
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
                    label = { Text("记录刚刚发生的内容") },
                    minLines = 3,
                )
                Button(
                    onClick = { onAppend(kind, eventText); eventText = "" },
                    enabled = eventText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("追加到时间线") }
                TextButton(onClick = { finishing = true }, modifier = Modifier.align(Alignment.End)) { Text("结束并整理") }
            } else {
                OutlinedTextField(summary, { summary = it }, modifier = Modifier.fillMaxWidth(), label = { Text("本节摘要") }, minLines = 2)
                OutlinedTextField(
                    transcript,
                    { transcript = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("课堂原文（可从 ChatGPT 一键分享或粘贴）") },
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
}
