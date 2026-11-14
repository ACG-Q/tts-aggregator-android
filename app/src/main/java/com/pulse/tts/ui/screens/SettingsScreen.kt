package com.pulse.tts.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pulse.tts.data.CallMode
import com.pulse.tts.data.ThemeMode
import com.pulse.tts.data.exportNodesJson
import com.pulse.tts.data.normalizeGithubProxy
import com.pulse.tts.playback.ReadTuning
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.AppTextField
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.components.SettingDivider
import com.pulse.tts.ui.components.SettingGroup
import com.pulse.tts.ui.components.SettingRow
import com.pulse.tts.ui.components.SettingRowLayout
import com.pulse.tts.ui.permissions.permissionBadgeText
import com.pulse.tts.ui.util.shortUrl
import com.pulse.tts.ui.util.voiceDisplayName
import com.pulse.tts.viewmodel.SettingsViewModel
import com.pulse.tts.viewmodel.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenLanSync: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = appViewModel {
        SettingsViewModel(it.nodeRepository, it.settings, it.logStore, it.voiceRepository)
    }
    val updateVm: UpdateViewModel = appViewModel { UpdateViewModel(it.updateRepository) }
    val updateState by updateVm.state.collectAsState()
    val appSettings by vm.appSettings.collectAsState()
    val synth by vm.synthSettings.collectAsState()
    val pool by vm.poolConfig.collectAsState()
    val nodes by vm.nodes.collectAsState()
    val logCount by vm.logCount.collectAsState()
    val voiceList by vm.voiceList.collectAsState()
    val importing by vm.importing.collectAsState()
    val version = remember { context.appVersion() }
    val scope = rememberCoroutineScope()

    var showPrivacy by remember { mutableStateOf(false) }
    var showClearLogs by remember { mutableStateOf(false) }
    var showProxyInput by remember { mutableStateOf(false) }
    var proxyDraft by remember { mutableStateOf("") }
    var proxyError by remember { mutableStateOf<String?>(null) }
    var permissionStates by remember { mutableStateOf(readPermissionStates(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStates = readPermissionStates(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { permissionStates = readPermissionStates(context) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    onToast("文件为空或无法读取")
                } else {
                    vm.importNodes(raw, onToast)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val json = exportNodesJson(nodes, appSettings, Instant.now().toString())
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val out = context.contentResolver.openOutputStream(uri)
                            ?: error("无法写入文件")
                        out.bufferedWriter().use { it.write(json) }
                    }.isSuccess
                }
                onToast(
                    if (ok) "已导出 ${nodes.size} 个节点（不含密钥）"
                    else "导出失败，请重试",
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "设置",
                subtitle = "声音、播放与节点调度",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SettingsSection("服务状态") {
                ValueSettingRow(
                    title = "系统 TTS 引擎",
                    subtitle = "已作为系统朗读引擎注册",
                    icon = Icons.Rounded.Headphones,
                    value = "已注册",
                    onClick = { openSystemTtsSettings(context, onToast) },
                )
                SettingDivider()
                ValueSettingRow(
                    title = "隐私",
                    subtitle = "了解文本与密钥的处理方式",
                    icon = Icons.Rounded.Security,
                    value = "说明",
                    onClick = { showPrivacy = true },
                )
            }

            SettingsSection("声音") {
                ValueSettingRow(
                    title = "默认声音",
                    subtitle = "点击选择音色与风格",
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    value = if (synth.voice.isBlank()) "节点默认"
                    else voiceDisplayName(voiceList, synth.voice, synth.style),
                    onClick = onOpenVoice,
                )
                SettingDivider()
                StepperSettingRow(
                    title = "默认语速",
                    subtitle = "作用于朗读与系统合成",
                    icon = Icons.Rounded.Speed,
                    value = ReadTuning.rateLabel(synth.rate),
                    onDecrease = { vm.stepRate(-1) },
                    onIncrease = { vm.stepRate(1) },
                )
                SettingDivider()
                StepperSettingRow(
                    title = "音调",
                    subtitle = "偏高或偏低的声音",
                    icon = Icons.Rounded.GraphicEq,
                    value = ReadTuning.pitchLabel(synth.pitch),
                    onDecrease = { vm.stepPitch(-1) },
                    onIncrease = { vm.stepPitch(1) },
                )
            }

            SettingsSection("播放") {
                SwitchSettingRow(
                    title = "后台与锁屏播放",
                    subtitle = "离开应用后继续朗读",
                    icon = Icons.Rounded.Headphones,
                    checked = appSettings.backgroundPlay,
                    onCheckedChange = { value -> vm.updateApp { it.copy(backgroundPlay = value) } },
                )
                SettingDivider()
                SwitchSettingRow(
                    title = "自动续听",
                    subtitle = "打开应用时继续上次朗读",
                    icon = Icons.Rounded.Animation,
                    checked = appSettings.resume,
                    onCheckedChange = { value -> vm.updateApp { it.copy(resume = value) } },
                )
                SettingDivider()
                ValueSettingRow(
                    title = "睡眠定时",
                    subtitle = "到时自动停止播放",
                    icon = Icons.Rounded.Bedtime,
                    value = sleepLabel(appSettings.sleepMinutes),
                    onClick = vm::cycleSleep,
                )
            }

            SettingsSection("调度与节点") {
                ValueSettingRow(
                    title = "默认模式",
                    subtitle = "自动切换、组轮询或手选节点",
                    icon = Icons.Rounded.SwapHoriz,
                    value = modeLabel(pool.mode),
                    onClick = vm::cycleMode,
                )
                SettingDivider()
                StepperSettingRow(
                    title = "失败冷却",
                    subtitle = "失败节点暂停参与调度",
                    icon = Icons.Rounded.Timer,
                    value = "${appSettings.cooldownMinutes} 分钟",
                    onDecrease = { vm.stepCooldown(-1) },
                    onIncrease = { vm.stepCooldown(1) },
                )
                SettingDivider()
                StepperSettingRow(
                    title = "失败阈值",
                    subtitle = "连续失败多少次后进入冷却",
                    icon = Icons.Rounded.Warning,
                    value = "${appSettings.failThreshold} 次",
                    onDecrease = { vm.stepFailThreshold(-1) },
                    onIncrease = { vm.stepFailThreshold(1) },
                )
                SettingDivider()
                SwitchSettingRow(
                    title = "启动时自动测试",
                    subtitle = "进入应用后探测节点健康",
                    icon = Icons.Rounded.Science,
                    checked = appSettings.autoTestOnLaunch,
                    onCheckedChange = { value -> vm.updateApp { it.copy(autoTestOnLaunch = value) } },
                )
                SettingDivider()
                ValueSettingRow(
                    title = "节点管理",
                    subtitle = "添加、测试与分组维护",
                    icon = Icons.Rounded.Dns,
                    value = "${nodes.size} 个",
                    onClick = onOpenAdmin,
                )
            }

            SettingsSection("数据") {
                SettingRow(
                    title = "局域网同步",
                    subtitle = "同一 Wi-Fi 下与电脑双向合并节点",
                    icon = Icons.Rounded.SwapHoriz,
                    onClick = onOpenLanSync,
                )
                SettingDivider()
                ValueSettingRow(
                    title = "导入节点",
                    subtitle = "JSON 含偏好设置，文本清单仅导入节点",
                    icon = Icons.Rounded.FileDownload,
                    value = if (importing) "导入中…" else "",
                    onClick = {
                        if (!importing) importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
                SettingDivider()
                SettingRow(
                    title = "导出配置",
                    subtitle = "导出节点与偏好设置，不含密钥",
                    icon = Icons.Rounded.FileUpload,
                    onClick = { exportLauncher.launch("tts-aggregator-config.json") },
                )
                SettingDivider()
                ValueSettingRow(
                    title = "清空调用日志",
                    subtitle = "删除全部记录，无法恢复",
                    icon = Icons.Rounded.DeleteSweep,
                    value = "$logCount 条",
                    onClick = { if (logCount > 0) showClearLogs = true else onToast("暂无调用日志") },
                )
            }

            SettingsSection("权限与外观") {
                ValueSettingRow(
                    title = "权限",
                    subtitle = "通知、电池优化与后台运行",
                    icon = Icons.Rounded.Lock,
                    value = permissionBadgeText(permissionStates),
                    onClick = onOpenPermissions,
                )
                SettingDivider()
                ValueSettingRow(
                    title = "外观",
                    subtitle = "跟随系统、浅色或深色",
                    icon = Icons.Rounded.Palette,
                    value = themeLabel(appSettings.theme),
                    onClick = vm::cycleTheme,
                )
                SettingDivider()
                SwitchSettingRow(
                    title = "减少动态效果",
                    subtitle = "关闭朗读页的脉动与缩放",
                    icon = Icons.Rounded.Animation,
                    checked = appSettings.reduceMotion,
                    onCheckedChange = { value -> vm.updateApp { it.copy(reduceMotion = value) } },
                )
                SettingDivider()
                SwitchSettingRow(
                    title = "触感反馈",
                    subtitle = "长按节点时轻微振动",
                    icon = Icons.Rounded.Vibration,
                    checked = appSettings.haptics,
                    onCheckedChange = { value -> vm.updateApp { it.copy(haptics = value) } },
                )
            }

            SettingsSection("应用") {
                ValueSettingRow(
                    title = "检查更新",
                    subtitle = "当前版本 v$version",
                    icon = Icons.Rounded.SystemUpdate,
                    value = if (updateState.checking) "检查中…" else "",
                    onClick = { if (!updateState.checking) updateVm.checkManually() },
                )
                SettingDivider()
                ValueSettingRow(
                    title = "GitHub 加速地址",
                    subtitle = "仅用于检查更新与下载 APK，留空直连",
                    icon = Icons.Rounded.Cloud,
                    value = appSettings.githubProxy
                        .takeIf { it.isNotBlank() }
                        ?.let { shortUrl(it, 22) }
                        ?: "直连",
                    onClick = {
                        proxyDraft = appSettings.githubProxy
                        proxyError = null
                        showProxyInput = true
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenAbout) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Pulse · 版本 $version",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私说明") },
            text = {
                Text(
                    "文本仅在本机与所选节点之间传输，不做云端中转。\n\n" +
                        "导出的配置文件不包含节点密钥；导入时也不会读取或还原密钥。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("知道了") }
            },
        )
    }

    if (showClearLogs) {
        AlertDialog(
            onDismissRequest = { showClearLogs = false },
            title = { Text("清空调用日志？") },
            text = { Text("将删除全部 $logCount 条记录，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearLogs()
                        showClearLogs = false
                        onToast("调用日志已清空")
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogs = false }) { Text("取消") }
            },
        )
    }

    if (showProxyInput) {
        AlertDialog(
            onDismissRequest = { showProxyInput = false },
            title = { Text("GitHub 加速地址") },
            text = {
                Column {
                    AppTextField(
                        value = proxyDraft,
                        onValueChange = {
                            proxyDraft = it
                            proxyError = null
                        },
                        placeholder = "https://ghproxy.net/",
                        isError = proxyError != null,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "填写加速服务前缀，例如 https://ghproxy.net/。留空表示直连。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    proxyError?.let { error ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = normalizeGithubProxy(proxyDraft)
                        if (normalized == null) {
                            proxyError = "地址必须以 http:// 或 https:// 开头"
                        } else {
                            vm.updateApp { it.copy(githubProxy = normalized) }
                            showProxyInput = false
                            onToast(if (normalized.isBlank()) "已改为直连" else "加速地址已保存")
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showProxyInput = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
    SettingGroup(content = content)
}

@Composable
private fun ValueSettingRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    value: String,
    onClick: () -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotBlank()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun StepperSettingRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    SettingRowLayout(
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = {
            IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = "减少$title",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "增加$title",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun modeLabel(mode: CallMode): String = when (mode) {
    CallMode.AUTO -> "自动"
    CallMode.GROUP -> "组轮询"
    CallMode.MANUAL -> "手选"
}

private fun sleepLabel(minutes: Int): String =
    if (minutes <= 0) "关闭" else "$minutes 分钟"

/**
 * 打开系统「文字转语音」设置页。
 *
 * 平台没有公开的 TTS 设置 action 常量，只能按各家设置应用的约定依次尝试：
 * AOSP/大多数定制 ROM 认 `com.android.settings.TTS_SETTINGS`，
 * 兜底退到无障碍设置（TTS 入口在该页下）。
 */
private fun openSystemTtsSettings(context: Context, onToast: (String) -> Unit) {
    val candidates = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    )
    val opened = candidates.any { intent ->
        runCatching { context.startActivity(intent) }.isSuccess
    }
    if (!opened) onToast("无法打开 TTS 设置，请在系统设置中搜索「文字转语音」")
}

internal fun Context.appVersion(): String = runCatching {
    @Suppress("DEPRECATION")
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrDefault("1.0")
