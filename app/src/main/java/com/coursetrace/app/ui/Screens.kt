@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.coursetrace.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.domain.ScheduledClass
import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Course
import com.coursetrace.app.model.ImportDraft
import com.coursetrace.app.model.LearningSession
import com.coursetrace.app.model.MaterialItem
import com.coursetrace.app.model.StudyProject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    state: AppState,
    activeSession: LearningSession?,
    onImportPdf: () -> Unit,
    onAddCourse: () -> Unit,
    onCourseClick: (Course) -> Unit,
    onStartSession: (String, String, String) -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    val today = now.toLocalDate()
    val classes = ScheduleEngine.classesOn(state, today)
    val next = classes.firstOrNull { it.end >= now }
    val term = state.activeTermId?.let { id -> state.terms.find { it.id == id } }
    val week = term?.let { ScheduleEngine.weekNumber(it, today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 22.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("课迹", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))}${week?.let { " · 第${it}周" }.orEmpty()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.School, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            if (next != null) {
                NextClassHero(
                    scheduled = next,
                    active = activeSession?.ownerId == next.course.id,
                    onClick = { onCourseClick(next.course) },
                    onStart = { onStartSession(next.course.id, "course", next.course.name) },
                )
            } else {
                EmptyTodayCard(onImportPdf, onAddCourse, classes.isNotEmpty())
            }
        }
        item {
            Text("快捷操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickAction("导入 PDF", Icons.Filled.PictureAsPdf, onImportPdf)
                QuickAction("添加课程", Icons.Filled.EditCalendar, onAddCourse)
                QuickAction("课堂记录", Icons.Filled.PlayArrow) {
                    next?.let { onStartSession(it.course.id, "course", it.course.name) }
                }
            }
        }
        item {
            Text("今日课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (classes.isEmpty()) {
            item {
                SoftCard {
                    Text("今天没有排课。可以安排 FPGA 或数电自学项目。")
                }
            }
        } else {
            items(classes, key = { it.slot.id + it.date }) { scheduled ->
                TimelineClassCard(scheduled, now, onCourseClick)
            }
        }
        item {
            val totalEvents = state.events.count { event ->
                state.sessions.find { it.id == event.sessionId }?.startedAt?.startsWith(today.toString()) == true
            }
            InsightCard(
                title = "今日学习脉络",
                text = if (totalEvents == 0) "开始课堂记录后，问题、错题和进度会按时间顺序沉淀在这里。"
                else "已按时间顺序保存 $totalEvents 条学习事件，原文同时进入本地 Git 历史。",
            )
        }
    }
}

@Composable
private fun NextClassHero(
    scheduled: ScheduledClass,
    active: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
) {
    val color = Color(scheduled.course.colorArgb.toInt())
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))))
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (scheduled.start <= LocalDateTime.now()) "正在上课" else "下一节",
                    color = Color.White.copy(alpha = .84f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White)
            }
            Spacer(Modifier.height(22.dp))
            Text(
                scheduled.course.name,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccessTime, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = .9f))
                Spacer(Modifier.width(6.dp))
                Text("${scheduled.start.toLocalTime()}–${scheduled.end.toLocalTime()}", color = Color.White)
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = .9f))
                Spacer(Modifier.width(4.dp))
                Text(scheduled.room.ifBlank { "教室待确认" }, color = Color.White)
            }
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = onStart, enabled = !active) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (active) "记录进行中" else "开始课堂记录")
            }
        }
    }
}

@Composable
private fun EmptyTodayCard(onImportPdf: () -> Unit, onAddCourse: () -> Unit, finished: Boolean = false) {
    SoftCard {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            if (finished) "今天的课程已结束" else "今天没有课程",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (finished) "今日课程记录仍可在下方查看。" else "从 PDF 识别，或手动添加第一节课。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onImportPdf) { Text("导入 PDF") }
            OutlinedButton(onClick = onAddCourse) { Text("手动添加") }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)),
    )
}

@Composable
private fun TimelineClassCard(
    scheduled: ScheduledClass,
    now: LocalDateTime,
    onCourseClick: (Course) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(54.dp).padding(top = 14.dp)) {
            Text(scheduled.start.toLocalTime().toString(), fontWeight = FontWeight.SemiBold)
            Text(scheduled.end.toLocalTime().toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .padding(top = 18.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(scheduled.course.colorArgb.toInt())),
        )
        Spacer(Modifier.width(12.dp))
        Card(
            onClick = { onCourseClick(scheduled.course) },
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = if (now in scheduled.start..scheduled.end) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(scheduled.course.name, fontWeight = FontWeight.Bold)
                Text(
                    listOf(scheduled.room, scheduled.course.teacher).filter(String::isNotBlank).joinToString(" · ").ifBlank { "信息待补充" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ScheduleScreen(
    state: AppState,
    onAddCourse: () -> Unit,
    onImportPdf: () -> Unit,
    onCourseClick: (Course) -> Unit,
    onCommitImport: (String) -> Unit,
    onManageTerms: () -> Unit,
) {
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var weekOverview by rememberSaveable { mutableStateOf(true) }
    val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val classes = ScheduleEngine.classesOn(state, selectedDate)
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCourse,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("添加课程") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 22.dp, 20.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("课程表", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            state.terms.find { it.id == state.activeTermId }?.name ?: "当前学期",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onManageTerms) { Text("学期设置") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedDate = selectedDate.minusWeeks(1) }) {
                        Icon(Icons.Outlined.ChevronLeft, "上一周")
                    }
                    Text(
                        "${monday.format(DateTimeFormatter.ofPattern("M月d日"))}–${monday.plusDays(6).format(DateTimeFormatter.ofPattern("M月d日"))}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedButton(onClick = { selectedDate = LocalDate.now() }) { Text("本周") }
                    IconButton(onClick = { selectedDate = selectedDate.plusWeeks(1) }) {
                        Icon(Icons.Outlined.ChevronRight, "下一周")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = weekOverview,
                        onClick = { weekOverview = true },
                        label = { Text("周总览") },
                    )
                    FilterChip(
                        selected = !weekOverview,
                        onClick = { weekOverview = false },
                        label = { Text("日详情") },
                    )
                }
            }
            if (weekOverview) {
                item {
                    WeeklyOverview(
                        state = state,
                        monday = monday,
                        onCourseClick = onCourseClick,
                        onOpenDay = { day ->
                            selectedDate = day
                            weekOverview = false
                        },
                    )
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(7) { offset ->
                            val day = monday.plusDays(offset.toLong())
                            val selected = day == selectedDate
                            Surface(
                                onClick = { selectedDate = day },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            ) {
                                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(ScheduleEngine.dayLabel(day.dayOfWeek.value).removePrefix("周"), style = MaterialTheme.typography.labelMedium)
                                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            items(state.importDrafts, key = { it.id }) { draft ->
                ImportDraftCard(draft, onCommitImport)
            }
            if (!weekOverview && classes.isEmpty()) {
                item {
                    SoftCard {
                        Icon(Icons.Outlined.Class, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("这一天没有课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("支持单双周、调课和跨学期管理。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = onImportPdf) { Text("从 PDF 导入") }
                    }
                }
            } else if (!weekOverview) {
                items(classes, key = { it.slot.id }) { TimelineClassCard(it, LocalDateTime.now(), onCourseClick) }
            }
        }
    }
}

@Composable
private fun WeeklyOverview(
    state: AppState,
    monday: LocalDate,
    onCourseClick: (Course) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(7) { offset ->
            val day = monday.plusDays(offset.toLong())
            val classes = ScheduleEngine.classesOn(state, day)
            Column(Modifier.width(148.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { onOpenDay(day) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (day == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ScheduleEngine.dayLabel(day.dayOfWeek.value), fontWeight = FontWeight.SemiBold)
                        Text(day.format(DateTimeFormatter.ofPattern("M/d")), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (classes.isEmpty()) {
                    Text(
                        "无课",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                classes.forEach { scheduled ->
                    val color = Color(scheduled.course.colorArgb.toInt())
                    Card(
                        onClick = { onCourseClick(scheduled.course) },
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .16f)),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                scheduled.course.name,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${scheduled.start.toLocalTime()}–${scheduled.end.toLocalTime()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                            Text(
                                scheduled.room.ifBlank { "教室待确认" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportDraftCard(draft: ImportDraft, onCommit: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("待核对的导入草稿", fontWeight = FontWeight.Bold)
                    Text(
                        "${draft.sourceName} · ${draft.slots.size} 条定时课程" +
                            if (draft.unscheduledCourses.isNotEmpty()) " · ${draft.unscheduledCourses.size} 门无固定时间" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    draft.termName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    Text(
                        "确认后写入当前选中的学期，不会按模型结果自动新建或切换学期",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                draft.slots.take(12).forEach { slot ->
                    val periods = slot.startPeriod?.let { first ->
                        "第${first}${slot.endPeriod?.takeIf { it != first }?.let { "-$it" }.orEmpty()}节 · "
                    }.orEmpty()
                    Text("${ScheduleEngine.dayLabel(slot.dayOfWeek)} ${periods}${slot.startTime}-${slot.endTime}  ${slot.courseName} · ${slot.room}")
                    if (slot.weeks.isNotEmpty()) Text("第 ${slot.weeks.joinToString(",")} 周", style = MaterialTheme.typography.labelSmall)
                    slot.warnings.forEach { Text("注意：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                    if (slot.confidence < .8f) Text("置信度 ${(slot.confidence * 100).toInt()}%", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                draft.unscheduledCourses.forEach { course ->
                    Text("无固定时间 · ${course.courseName} · 第 ${course.weeks.joinToString(",")} 周")
                }
                draft.warnings.forEach { Text("注意：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (draft.termStartDate != null) {
                    Text(
                        "识别建议的开学日：${draft.termStartDate}（已有课程时不会覆盖当前学期起点）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onCommit(draft.id) }, modifier = Modifier.fillMaxWidth()) { Text("确认并导入") }
            }
        }
    }
}

@Composable
fun ProjectsScreen(
    state: AppState,
    onAddProject: () -> Unit,
    onProjectClick: (StudyProject) -> Unit,
    onStartSession: (String, String, String) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProject,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("新建项目") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 22.dp, 20.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("自学项目", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("不设闹钟，也能拥有资料、进度与 ChatGPT 联动。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.projects.none { !it.archived }) {
                item {
                    SoftCard {
                        Icon(Icons.Outlined.School, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(12.dp))
                        Text("建立第一个学习项目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("例如 FPGA、数字电路或英语阅读。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onAddProject) { Text("新建项目") }
                    }
                }
            }
            items(state.projects.filterNot { it.archived }, key = { it.id }) { project ->
                val sessionCount = state.sessions.count { it.ownerId == project.id }
                Card(onClick = { onProjectClick(project) }, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = Color(project.colorArgb.toInt()).copy(alpha = .18f)) {
                                Icon(Icons.Outlined.AutoStories, null, Modifier.padding(10.dp), tint = Color(project.colorArgb.toInt()))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$sessionCount 次学习记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onStartSession(project.id, "project", project.name) }) { Icon(Icons.Filled.PlayArrow, "开始学习") }
                        }
                        if (project.currentProgress.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("当前进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(project.currentProgress, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaterialsScreen(
    state: AppState,
    ownerName: (String) -> String,
    onAddMaterial: (MaterialOwner) -> Unit,
) {
    val context = LocalContext.current
    var selectedOwner by remember {
        mutableStateOf(
            state.courses.firstOrNull { !it.archived }?.let { MaterialOwner(it.id, "course", it.name) }
                ?: state.projects.firstOrNull()?.let { MaterialOwner(it.id, "project", it.name) },
        )
    }
    val owners = state.courses.filterNot { it.archived }.map { MaterialOwner(it.id, "course", it.name) } +
        state.projects.map { MaterialOwner(it.id, "project", it.name) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 22.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("资料库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("按课程与项目分类；大文件不写入 Git，只记录索引与校验值。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                owners.forEach { owner ->
                    AssistChip(onClick = { selectedOwner = owner }, label = { Text(owner.name) }, leadingIcon = {
                        if (owner == selectedOwner) Icon(Icons.Outlined.Verified, null, Modifier.size(18.dp))
                    })
                }
            }
        }
        item {
            Button(
                onClick = { selectedOwner?.let(onAddMaterial) },
                enabled = selectedOwner != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AttachFile, null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedOwner == null) "先创建课程或项目" else "给 ${selectedOwner?.name} 添加资料")
            }
        }
        if (state.materials.isEmpty()) {
            item {
                SoftCard {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("资料库还是空的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("通过系统文件选择器授权，课迹不会扫描无关文件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(state.materials, key = { it.id }) { item ->
            MaterialCard(item, ownerName(item.ownerId)) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.uri)).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }
        }
    }
}

@Composable
private fun MaterialCard(item: MaterialItem, owner: String, onOpen: () -> Unit) {
    Card(onClick = onOpen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$owner · ${item.kind.name.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
        }
    }
}

@Composable
fun SoftCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun InsightCard(title: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(18.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .78f))
            }
        }
    }
}
