package com.lecheeel.memento.ui

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lecheeel.memento.data.AppSettings
import com.lecheeel.memento.data.FilterMode
import com.lecheeel.memento.data.NotificationRepository
import com.lecheeel.memento.notification.KeepAliveService
import com.lecheeel.memento.ui.theme.StatusError
import com.lecheeel.memento.ui.theme.StatusGood
import com.lecheeel.memento.ui.theme.StatusNeutral
import com.lecheeel.memento.ui.theme.StatusWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap? = null,
)

private enum class StatusTone(val color: Color) {
    Good(StatusGood),
    Warn(StatusWarn),
    Error(StatusError),
    Neutral(StatusNeutral),
}

private fun queueTone(size: Int): StatusTone = when {
    size >= 100 -> StatusTone.Error
    size >= 20 -> StatusTone.Warn
    else -> StatusTone.Neutral
}

// ═══════════════════════ 顶部导航栏 ═══════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

// ═══════════════════════ 首页 ═══════════════════════

@Composable
fun HomeScreen(
    settings: AppSettings,
    listenerGranted: Boolean,
    queueSize: Int,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onToggleCapture: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Memento",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "把通知变成你的 AI 记忆",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
        }

        Card(
            colors = CardDefaults.cardColors(),
            border = if (!listenerGranted) BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)) else null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前状态", fontWeight = FontWeight.SemiBold)
                StatusLine(
                    label = "通知访问",
                    value = if (listenerGranted) "已授权" else "未授权",
                    tone = if (listenerGranted) StatusTone.Good else StatusTone.Error,
                )
                StatusLine(
                    label = "自动采集",
                    value = if (settings.captureEnabled) "运行中" else "已停止",
                    tone = if (settings.captureEnabled) StatusTone.Good else StatusTone.Neutral,
                )
                StatusLine(
                    label = "过滤模式",
                    value = if (settings.filterMode == FilterMode.WHITELIST) "白名单" else "黑名单",
                    tone = StatusTone.Neutral,
                )
                StatusLine(
                    label = "通知保活",
                    value = if (settings.keepAliveEnabled) "开启" else "关闭",
                    tone = if (settings.keepAliveEnabled) StatusTone.Good else StatusTone.Neutral,
                )
                StatusLine(
                    label = "待同步队列",
                    value = "$queueSize 条",
                    tone = queueTone(queueSize),
                )
                if (!listenerGranted) {
                    HorizontalDivider()
                    Text(
                        "未授予通知访问权限，Memento 无法采集任何通知。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onOpenNotificationSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("打开通知使用权")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("采集与同步", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.captureEnabled) "通知会自动采集并上传" else "通知不会进入队列",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.captureEnabled, onCheckedChange = onToggleCapture)
            }
        }
    }
}

// ═══════════════════════ 设置页 ═══════════════════════

@Composable
fun SettingsScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onOpenApps: () -> Unit,
) {
    val appContext = LocalContext.current

    var filterMode by remember(settings.filterMode) { mutableStateOf(settings.filterMode) }
    var redactSensitiveText by remember(settings.redactSensitiveText) { mutableStateOf(settings.redactSensitiveText) }
    var keepAliveEnabled by remember(settings.keepAliveEnabled) { mutableStateOf(settings.keepAliveEnabled) }
    var useDynamicColor by remember(settings.useDynamicColor) { mutableStateOf(settings.useDynamicColor) }

    val serverUrlState = remember { mutableStateOf(settings.serverBaseUrl) }
    val authTokenState = remember { mutableStateOf(settings.authToken) }
    val encryptionSecretState = remember { mutableStateOf(settings.encryptionSecret) }
    val keywordFiltersState = remember { mutableStateOf(settings.keywordFiltersCsv) }

    fun commitAdvancedFields(notify: Boolean = false) {
        NotificationRepository.update {
            it.copy(
                serverBaseUrl = serverUrlState.value.trim(),
                authToken = authTokenState.value.trim(),
                encryptionSecret = encryptionSecretState.value.trim(),
                keywordFiltersCsv = keywordFiltersState.value.trim(),
            )
        }
        if (notify) {
            Toast.makeText(appContext, "设置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    // 离开页面时兜底提交尚未失焦的输入内容
    DisposableEffect(Unit) {
        onDispose { commitAdvancedFields() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("过滤模式", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = filterMode == FilterMode.WHITELIST,
                        onClick = {
                            filterMode = FilterMode.WHITELIST
                            NotificationRepository.update { it.copy(filterMode = FilterMode.WHITELIST) }
                        },
                        label = { Text("白名单") },
                    )
                    FilterChip(
                        selected = filterMode == FilterMode.BLACKLIST,
                        onClick = {
                            filterMode = FilterMode.BLACKLIST
                            NotificationRepository.update { it.copy(filterMode = FilterMode.BLACKLIST) }
                        },
                        label = { Text("黑名单") },
                    )
                }
                Text(
                    if (filterMode == FilterMode.WHITELIST) "只采集勾选应用的通知" else "采集除勾选应用外的所有通知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("应用选择", fontWeight = FontWeight.SemiBold)
                        Text(
                            "已选择 ${parsePackageCsv(settings.selectedPackagesCsv).size} 个应用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onOpenApps) { Text("管理") }
                }
                Text(
                    if (settings.filterMode == FilterMode.WHITELIST) "白名单模式：仅采集勾选应用" else "黑名单模式：排除勾选应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("服务器配置", fontWeight = FontWeight.SemiBold)
                Text(
                    "用于接收加密通知的自托管服务器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsTextField(
                    label = "服务器地址",
                    value = serverUrlState.value,
                    initial = settings.serverBaseUrl,
                    onValueChange = { serverUrlState.value = it },
                    onCommit = { value -> if (value != settings.serverBaseUrl) commitAdvancedFields(notify = true) },
                    keyboardType = KeyboardType.Uri,
                )
                SettingsTextField(
                    label = "设备令牌",
                    value = authTokenState.value,
                    initial = settings.authToken,
                    onValueChange = { authTokenState.value = it },
                    onCommit = { value -> if (value != settings.authToken) commitAdvancedFields(notify = true) },
                )
                SettingsTextField(
                    label = "加密密钥",
                    value = encryptionSecretState.value,
                    initial = settings.encryptionSecret,
                    onValueChange = { encryptionSecretState.value = it },
                    onCommit = { value -> if (value != settings.encryptionSecret) commitAdvancedFields(notify = true) },
                )
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("隐私与过滤", fontWeight = FontWeight.SemiBold)
                Text(
                    "敏感信息脱敏与关键词过滤",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsTextField(
                    label = "敏感关键词（逗号分隔）",
                    value = keywordFiltersState.value,
                    initial = settings.keywordFiltersCsv,
                    onValueChange = { keywordFiltersState.value = it },
                    onCommit = { value -> if (value != settings.keywordFiltersCsv) commitAdvancedFields(notify = true) },
                )
                SwitchRow(
                    title = "敏感文本脱敏",
                    subtitle = "验证码、OTP 等敏感内容将被打码",
                    checked = redactSensitiveText,
                    onCheckedChange = {
                        redactSensitiveText = it
                        NotificationRepository.update { current -> current.copy(redactSensitiveText = it) }
                    },
                )
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("系统", fontWeight = FontWeight.SemiBold)
                SwitchRow(
                    title = "通知保活",
                    subtitle = "降低系统回收采集服务的概率",
                    checked = keepAliveEnabled,
                    onCheckedChange = {
                        keepAliveEnabled = it
                        NotificationRepository.update { current -> current.copy(keepAliveEnabled = it) }
                        KeepAliveService.setEnabled(appContext, it)
                    },
                )
                SwitchRow(
                    title = "动态取色",
                    subtitle = "跟随系统壁纸配色（Android 12+）",
                    checked = useDynamicColor,
                    onCheckedChange = {
                        useDynamicColor = it
                        NotificationRepository.update { current -> current.copy(useDynamicColor = it) }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

// ═══════════════════════ 应用列表页 ═══════════════════════

@Composable
fun AppListScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val installedApps by produceState<List<InstalledApp>?>(initialValue = null, context) {
        value = withContext(Dispatchers.Default) { loadInstalledApps(context) }
    }
    var selectedPackages by remember(settings.selectedPackagesCsv) {
        mutableStateOf(parsePackageCsv(settings.selectedPackagesCsv))
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by remember(settings.showSystemApps) { mutableStateOf(settings.showSystemApps) }

    fun commitSelection(updated: Set<String>) {
        selectedPackages = updated
        NotificationRepository.update { it.copy(selectedPackagesCsv = updated.sorted().joinToString(",")) }
    }

    val visibleApps = installedApps.orEmpty()
        .asSequence()
        .filter { showSystemApps || !isSystemApp(context, it.packageName) }
        .filter {
            val lower = query.trim().lowercase()
            lower.isBlank() ||
                it.label.lowercase().contains(lower) ||
                it.packageName.lowercase().contains(lower)
        }
        .sortedWith(
            compareByDescending<InstalledApp> { selectedPackages.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName },
        )
        .toList()

    val hint = when {
        settings.filterMode == FilterMode.WHITELIST && selectedPackages.isEmpty() ->
            "白名单模式未勾选任何应用，将不会采集任何通知"
        settings.filterMode == FilterMode.WHITELIST -> "白名单模式：仅采集以下勾选应用的通知"
        settings.filterMode == FilterMode.BLACKLIST && selectedPackages.isEmpty() ->
            "黑名单模式：未勾选应用，将采集所有通知"
        else -> "黑名单模式：采集除勾选应用外的所有通知"
    }
    val hintRisky = settings.filterMode == FilterMode.WHITELIST && selectedPackages.isEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow(
                    title = "显示系统应用",
                    subtitle = "是否列出系统预装应用",
                    checked = showSystemApps,
                    onCheckedChange = {
                        showSystemApps = it
                        NotificationRepository.update { current -> current.copy(showSystemApps = it) }
                    },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用或包名") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已勾选 ${selectedPackages.size} 个 · 可见 ${visibleApps.size} 个",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { commitSelection(visibleApps.map { it.packageName }.toSet()) },
                            enabled = visibleApps.isNotEmpty(),
                        ) { Text("全选可见") }
                        TextButton(
                            onClick = { commitSelection(emptySet()) },
                            enabled = selectedPackages.isNotEmpty(),
                        ) { Text("清空") }
                    }
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hintRisky) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            installedApps == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            visibleApps.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (query.isNotBlank()) "未找到匹配的应用" else "暂无可显示的应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    Column {
                        AppPackageRow(
                            app = app,
                            checked = selectedPackages.contains(app.packageName),
                            onClick = {
                                val updated = if (selectedPackages.contains(app.packageName)) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                                commitSelection(updated)
                            },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════ 通用组件 ═══════════════════════

@Composable
private fun StatusLine(label: String, value: String, tone: StatusTone) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tone.color),
            )
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    initial: String,
    onValueChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused && value != initial) onCommit(value.trim()) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(value.trim()) }),
    )
}

@Composable
private fun AppPackageRow(
    app: InstalledApp,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            app.icon?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════ 工具函数 ═══════════════════════

private fun parsePackageCsv(value: String): Set<String> =
    value.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
    return packageManager
        .getInstalledApplications(PackageManager.GET_META_DATA)
        .map { appInfo ->
            val icon = runCatching {
                appInfo.loadIcon(packageManager).toBitmap(72, 72).asImageBitmap()
            }.getOrNull()
            InstalledApp(
                label = appInfo.loadLabel(packageManager).toString(),
                packageName = appInfo.packageName,
                icon = icon,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<InstalledApp> { it.label.lowercase() }.thenBy { it.packageName })
}

private fun isSystemApp(context: Context, packageName: String): Boolean {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
    } catch (_: Throwable) {
        false
    }
}
