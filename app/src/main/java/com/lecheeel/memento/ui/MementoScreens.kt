package com.lecheeel.memento.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lecheeel.memento.data.AppSettings
import com.lecheeel.memento.data.FilterMode
import com.lecheeel.memento.data.NotificationRepository
import com.lecheeel.memento.notification.KeepAliveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val label: String,
    val packageName: String,
)

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Memento", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onOpenSettings) {
                Text("设置")
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前状态", fontWeight = FontWeight.SemiBold)
                StatusLine("通知访问", if (listenerGranted) "已授权" else "未授权")
                StatusLine("自动采集", if (settings.captureEnabled) "运行中" else "已停止")
                StatusLine("自动同步", if (settings.captureEnabled) "开启" else "关闭")
                StatusLine("通知保活", if (settings.keepAliveEnabled) "开启" else "关闭")
                StatusLine("过滤模式", if (settings.filterMode == FilterMode.WHITELIST) "白名单" else "黑名单")
                StatusLine("待同步队列", queueSize.toString())
                if (!listenerGranted) {
                    HorizontalDivider()
                    Button(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
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
                Column {
                    Text("采集与同步", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.captureEnabled) "通知会自动采集并上传" else "通知不会进入队列",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.captureEnabled,
                    onCheckedChange = onToggleCapture,
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
) {
    val appContext = LocalContext.current
    var filterMode by remember(settings.filterMode) { mutableStateOf(settings.filterMode) }
    var serverUrl by remember(settings.serverBaseUrl) { mutableStateOf(settings.serverBaseUrl) }
    var authToken by remember(settings.authToken) { mutableStateOf(settings.authToken) }
    var encryptionSecret by remember(settings.encryptionSecret) { mutableStateOf(settings.encryptionSecret) }
    var keywordFilters by remember(settings.keywordFiltersCsv) { mutableStateOf(settings.keywordFiltersCsv) }
    var redactSensitiveText by remember(settings.redactSensitiveText) { mutableStateOf(settings.redactSensitiveText) }
    var keepAliveEnabled by remember(settings.keepAliveEnabled) { mutableStateOf(settings.keepAliveEnabled) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }

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
                    if (filterMode == FilterMode.WHITELIST) "只采集勾选的应用" else "采集除勾选应用外的通知",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("应用选择", fontWeight = FontWeight.SemiBold)
                Text("在独立页面中管理白名单/黑名单应用", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                    Text("打开应用列表")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("高级设置", fontWeight = FontWeight.SemiBold)
                        Text("服务器、密钥与敏感词", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                        Text(if (advancedExpanded) "收起" else "展开")
                    }
                }
                if (advancedExpanded) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            NotificationRepository.update { current -> current.copy(serverBaseUrl = it) }
                        },
                        label = { Text("服务器地址") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = authToken,
                        onValueChange = {
                            authToken = it
                            NotificationRepository.update { current -> current.copy(authToken = it) }
                        },
                        label = { Text("设备令牌") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = encryptionSecret,
                        onValueChange = {
                            encryptionSecret = it
                            NotificationRepository.update { current -> current.copy(encryptionSecret = it) }
                        },
                        label = { Text("加密密钥") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = keywordFilters,
                        onValueChange = {
                            keywordFilters = it
                            NotificationRepository.update { current -> current.copy(keywordFiltersCsv = it) }
                        },
                        label = { Text("敏感关键词，逗号分隔") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("敏感文本脱敏")
                        Switch(
                            checked = redactSensitiveText,
                            onCheckedChange = {
                                redactSensitiveText = it
                                NotificationRepository.update { current -> current.copy(redactSensitiveText = it) }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("通知保活")
                        Switch(
                            checked = keepAliveEnabled,
                            onCheckedChange = {
                                keepAliveEnabled = it
                                NotificationRepository.update { current -> current.copy(keepAliveEnabled = it) }
                                KeepAliveService.setEnabled(appContext, it)
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun AppListScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val installedApps by produceState<List<InstalledApp>>(initialValue = emptyList(), context) {
        value = withContext(Dispatchers.Default) { loadInstalledApps(context) }
    }

    var selectedPackages by remember(settings.selectedPackagesCsv) {
        mutableStateOf(parsePackageCsv(settings.selectedPackagesCsv))
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by remember(settings.showSystemApps) { mutableStateOf(settings.showSystemApps) }

    val visibleApps = installedApps
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("应用列表", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("系统应用", fontWeight = FontWeight.SemiBold)
                        Text("切换是否把系统预装应用列出来", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = showSystemApps,
                        onCheckedChange = {
                            showSystemApps = it
                            NotificationRepository.update { current -> current.copy(showSystemApps = it) }
                        },
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用或包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("已勾选 ${selectedPackages.size} 个应用", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        AppPackageRow(
                            app = app,
                            checked = selectedPackages.contains(app.packageName),
                            onCheckedChange = { checked ->
                                selectedPackages = if (checked) {
                                    selectedPackages + app.packageName
                                } else {
                                    selectedPackages - app.packageName
                                }
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppPackageRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(app.label, fontWeight = FontWeight.Medium)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
        }
    }
}

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
            InstalledApp(
                label = appInfo.loadLabel(packageManager).toString(),
                packageName = appInfo.packageName,
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
