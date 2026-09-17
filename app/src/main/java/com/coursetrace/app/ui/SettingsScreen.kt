@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.coursetrace.app.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.coursetrace.app.model.ApiProfile
import com.coursetrace.app.model.AppPreferences
import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.ScheduleTimeProfile
import com.coursetrace.app.model.ThemeMode
import com.coursetrace.app.BuildConfig

@Composable
fun SettingsScreen(
    state: AppState,
    hasApiKey: Boolean,
    hasRelayToken: Boolean,
    busy: Boolean,
    onUpdatePreferences: ((AppPreferences) -> AppPreferences) -> Unit,
    onSaveApi: (ApiProfile, String?) -> Unit,
    onTestApi: (ApiProfile, String?) -> Unit,
    onSetEarlyAlarm: () -> Unit,
    onSaveChatGptLink: (Boolean, String, String?) -> Unit,
    onSyncChatGpt: () -> Unit,
    onCheckUpdate: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val context = LocalContext.current
    var showApiDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showTimeProfileDialog by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 22.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("外观、提醒、模型与本地数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsSection("外观", Icons.Outlined.Brightness6) {
                Text("主题", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        }
                        val icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Outlined.Brightness6
                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                        }
                        FilterChip(
                            selected = state.preferences.themeMode == mode,
                            onClick = { onUpdatePreferences { it.copy(themeMode = mode) } },
                            label = { Text(label) },
                            leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                SettingSwitch(
                    title = "系统动态配色",
                    subtitle = "Android 12 及以上使用壁纸色彩",
                    checked = state.preferences.dynamicColor,
                    onChecked = { enabled -> onUpdatePreferences { it.copy(dynamicColor = enabled) } },
                )
                SettingSwitch(
                    title = "减少动画",
                    subtitle = "减少大幅位移，保留必要状态反馈",
                    checked = state.preferences.reduceMotion,
                    onChecked = { enabled -> onUpdatePreferences { it.copy(reduceMotion = enabled) } },
                )
            }
        }
        item {
            SettingsSection("上课提醒", Icons.Outlined.NotificationsActive) {
                Text("默认提前时间", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20, 30, 60).forEach { minute ->
                        FilterChip(
                            selected = state.preferences.notificationLeadMinutes == minute,
                            onClick = { onUpdatePreferences { it.copy(notificationLeadMinutes = minute) } },
                            label = { Text("$minute 分钟") },
                        )
                    }
                }
                Text(
                    "Android 16 会在临近/上课期间请求实时更新样式；小米超级岛是否展示仍由系统判定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "检查精确提醒权限" else "Android 12 以下无需单独授权")
                }
            }
        }
        item {
            SettingsSection("校区作息", Icons.Outlined.Schedule) {
                Text(state.preferences.scheduleTimeProfile.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "PDF 只有“第几节”时，按此方案换算真实时间；当前默认与你提供的国际校区作息表一致。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showTimeProfileDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("查看第 1–11 节时间") }
            }
        }
        item {
            SettingsSection("早课闹钟", Icons.Outlined.Alarm) {
                SettingSwitch(
                    title = "自动准备早课闹钟",
                    subtitle = "默认早于 9:30 的课程，提前 90 分钟",
                    checked = state.preferences.earlyAlarmEnabled,
                    onChecked = { enabled -> onUpdatePreferences { it.copy(earlyAlarmEnabled = enabled) } },
                )
                AnimatedVisibility(state.preferences.earlyAlarmEnabled) {
                    Column {
                        Text(
                            "系统闹钟由手机时钟应用最终确认；课迹不会绕过系统或静默修改。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onSetEarlyAlarm) { Text("设置下一次早课闹钟") }
                    }
                }
            }
        }
        item {
            SettingsSection("PDF 识别接口", Icons.Outlined.Api) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.preferences.apiProfile.model, fontWeight = FontWeight.SemiBold)
                        Text(
                            state.preferences.apiProfile.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (hasApiKey) Icons.Filled.CheckCircle else Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = if (hasApiKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    )
                    IconButton(onClick = { showApiDialog = true }) { Icon(Icons.Outlined.ChevronRight, "编辑") }
                }
                Text(
                    "API Key 仅以 Android Keystore 加密保存在本机，不写入 APK、Git 或日志。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsSection("ChatGPT Mobile 联动", Icons.AutoMirrored.Outlined.Chat) {
                Text("Pro 手机端：分享联动可立即使用", fontWeight = FontWeight.SemiBold)
                Text(
                    "当前个人 Pro 手机端不支持自定义 MCP 写入。课迹采用系统分享：把提示词发给 ChatGPT，结束后把 JSON/原文分享到课迹；仍只使用你的 Pro 对话额度。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val prompt = "请按时间顺序记录本次课堂讨论，区分问题、错题、痛点、进度和待办。下课时保留课堂原文，并明确标记原文是否完整；输出可分享给课迹的结构化 JSON。"
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("课迹课堂指令", prompt))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("复制手机端课堂指令") }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (state.preferences.chatGptLink.enabled) "已启用 · ${state.preferences.chatGptLink.lastSyncAt ?: "等待首次同步"}"
                    else "实验性 MCP 中继：仅供未来开放或兼容的 Web/工作区使用",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showLinkDialog = true }, modifier = Modifier.weight(1f)) { Text("实验性中继") }
                    Button(
                        onClick = onSyncChatGpt,
                        enabled = state.preferences.chatGptLink.enabled && !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (busy) "同步中…" else "立即同步") }
                }
            }
        }
        item {
            SettingsSection("数据与隐私", Icons.Outlined.Security) {
                SettingSwitch(
                    title = "默认保存课堂原文",
                    subtitle = "原文和结构化事件一并进入本地 Git",
                    checked = state.preferences.rawTranscriptByDefault,
                    onChecked = { enabled -> onUpdatePreferences { it.copy(rawTranscriptByDefault = enabled) } },
                )
                SettingSwitch(
                    title = "锁屏隐藏敏感内容",
                    subtitle = "通知只显示课程概要",
                    checked = state.preferences.hideSensitiveOnLockScreen,
                    onChecked = { enabled -> onUpdatePreferences { it.copy(hideSensitiveOnLockScreen = enabled) } },
                )
                SettingSwitch(
                    title = "生物识别应用锁",
                    subtitle = "离开应用后再次进入需要设备凭据",
                    checked = state.preferences.biometricLock,
                    onChecked = { enabled -> onUpdatePreferences { it.copy(biometricLock = enabled) } },
                )
                SettingSwitch(
                    title = "可选加密远程备份",
                    subtitle = "默认关闭；客户端 AES-256-GCM 加密后再上传",
                    checked = state.preferences.encryptedRemoteBackupEnabled,
                    onChecked = { enabled -> onUpdatePreferences { it.copy(encryptedRemoteBackupEnabled = enabled) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("本地 Git 是主记录；卸载前请导出备份。", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Backup, null)
                    Spacer(Modifier.width(8.dp))
                    Text("导出 AES-256 加密备份")
                }
                TextButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Text("从加密备份恢复") }
            }
        }
        item {
            SettingsSection("应用更新", Icons.Outlined.SystemUpdate) {
                Text("${if (state.preferences.updateChannel == "stable") "稳定" else "测试"}通道 · 当前 ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Text(
                    "支持 HTTPS 签名清单、SHA-256 校验与系统安装器确认。无法静默安装，也不会请求 root。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showUpdateDialog = true }, modifier = Modifier.weight(1f)) { Text("更新设置") }
                    Button(onClick = onCheckUpdate, enabled = !busy, modifier = Modifier.weight(1f)) { Text("检查并安装") }
                }
            }
        }
        item {
            Text(
                "课迹只使用公开 Android API。无无障碍自动化、无屏幕监听、无隐藏接口、无广告分析。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    if (showApiDialog) {
        ApiSettingsDialog(
            initial = state.preferences.apiProfile,
            hasSavedKey = hasApiKey,
            busy = busy,
            onDismiss = { showApiDialog = false },
            onSave = { profile, key ->
                onSaveApi(profile, key)
                showApiDialog = false
            },
            onTest = onTestApi,
        )
    }
    if (showLinkDialog) {
        ChatGptLinkDialog(
            enabled = state.preferences.chatGptLink.enabled,
            initialUrl = state.preferences.chatGptLink.relayUrl,
            hasSavedToken = hasRelayToken,
            onDismiss = { showLinkDialog = false },
            onSave = { enabled, url, token ->
                onSaveChatGptLink(enabled, url, token)
                showLinkDialog = false
            },
        )
    }
    if (showUpdateDialog) {
        UpdateSettingsDialog(
            initialUrl = state.preferences.updateManifestUrl,
            initialChannel = state.preferences.updateChannel,
            onDismiss = { showUpdateDialog = false },
            onSave = { url, channel ->
                onUpdatePreferences { it.copy(updateManifestUrl = url.trim(), updateChannel = channel) }
                showUpdateDialog = false
            },
        )
    }
    if (showTimeProfileDialog) {
        TimeProfileDialog(
            profile = state.preferences.scheduleTimeProfile,
            onDismiss = { showTimeProfileDialog = false },
        )
    }
}

@Composable
private fun TimeProfileDialog(
    profile: ScheduleTimeProfile,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.name) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profile.periods.sortedBy { it.period }.forEach { period ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("第 ${period.period} 节", fontWeight = FontWeight.Medium)
                        Text("${period.startTime}–${period.endTime}")
                    }
                }
                HorizontalDivider(Modifier.padding(top = 6.dp))
                Text(
                    "午休 12:15–14:00；晚间休息 17:20–19:00。识别结果仍会进入导入预览，由你确认后才写入课表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChecked)
    }
}

@Composable
private fun ApiSettingsDialog(
    initial: ApiProfile,
    hasSavedKey: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ApiProfile, String?) -> Unit,
    onTest: (ApiProfile, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var model by remember { mutableStateOf(initial.model) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var backendUrl by remember { mutableStateOf(initial.backendUrl) }
    var useBackend by remember { mutableStateOf(initial.useBackendProxy) }
    var advanced by remember { mutableStateOf(false) }
    val profile = ApiProfile(name, baseUrl.trimEnd('/'), model, useBackend, backendUrl.trimEnd('/'))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型与接口") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称") },
                    supportingText = { Text("同一中转站切换模型通常只改这里") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (hasSavedKey) "API Key（留空则保持原值）" else "API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起高级设置" else "更改中转站或后端") }
                AnimatedVisibility(advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text("配置名称") }, singleLine = true)
                        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("OpenAI 兼容 Base URL") }, singleLine = true)
                        SettingSwitch("通过自己的后端代理", "避免 API Key 直接离开你的设备", useBackend) { useBackend = it }
                        if (useBackend) OutlinedTextField(backendUrl, { backendUrl = it }, label = { Text("后端地址") }, singleLine = true)
                    }
                }
                OutlinedButton(
                    onClick = { onTest(profile, apiKey.takeIf(String::isNotBlank)) },
                    enabled = !busy && (apiKey.isNotBlank() || hasSavedKey),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "测试中…" else "测试连接") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(profile, apiKey.takeIf(String::isNotBlank)) },
                enabled = model.isNotBlank() && baseUrl.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChatGptLinkDialog(
    enabled: Boolean,
    initialUrl: String,
    hasSavedToken: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean, String, String?) -> Unit,
) {
    var linkEnabled by remember { mutableStateOf(enabled) }
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("实验性 MCP 中继") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingSwitch(
                    title = "启用 MCP 中继同步",
                    subtitle = "每 15 分钟低耗电同步，也可手动触发",
                    checked = linkEnabled,
                    onChecked = { linkEnabled = it },
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("中继地址") },
                    placeholder = { Text("https://coursetrace.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(if (hasSavedToken) "配对令牌（留空保持原值）" else "至少 32 位配对令牌") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "同一令牌同时配置到自托管中继与手机。当前个人 Pro 手机端不能使用自定义 MCP 写入；此入口为未来支持及兼容的 Web/工作区保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(linkEnabled, url, token.takeIf(String::isNotBlank)) },
                enabled = !linkEnabled || (url.isNotBlank() && (hasSavedToken || token.length >= 32)),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UpdateSettingsDialog(
    initialUrl: String,
    initialChannel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var channel by remember { mutableStateOf(initialChannel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("HTTPS 签名清单地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = channel == "stable", onClick = { channel = "stable" }, label = { Text("稳定") })
                    FilterChip(selected = channel == "beta", onClick = { channel = "beta" }, label = { Text("测试") })
                }
                Text("签名私钥保存在开发电脑的 .secrets 目录，不会进入 Git 或 APK。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(url, channel) }, enabled = url.isBlank() || url.startsWith("https://")) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
