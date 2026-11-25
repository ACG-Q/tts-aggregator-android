package com.pulse.tts.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pulse.tts.data.CallMode
import com.pulse.tts.data.ThemeMode
import com.pulse.tts.data.UpdateChannel
import com.pulse.tts.playback.ReadTuning
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.components.SettingConfirmSheet
import com.pulse.tts.ui.components.SettingDivider
import com.pulse.tts.ui.components.SettingPickerSheet
import com.pulse.tts.ui.components.SettingRowLayout
import com.pulse.tts.ui.components.SettingSection
import com.pulse.tts.ui.components.SettingSheetOption
import com.pulse.tts.ui.components.SettingStepperRow
import com.pulse.tts.ui.components.SettingSwitchRow
import com.pulse.tts.ui.components.SettingValueRow
import com.pulse.tts.ui.components.SettingValueTrailing
import com.pulse.tts.ui.permissions.permissionBadgeText
import com.pulse.tts.ui.util.voiceDisplayName
import com.pulse.tts.viewmodel.SettingsViewModel
import com.pulse.tts.viewmodel.UpdateViewModel

/** 首页自带的选择类托盘，避免多处布尔状态互相覆盖。 */
private enum class SettingsPicker { SLEEP, MODE }

/**
 * 设置首页：只放高频项，低频项收进「更多设置」。
 *
 * 分组固定为 朗读 / 节点与调度 / 外观与权限 / 应用。
 * 可选值统一走底部托盘；「检查更新」同时承担主操作与渠道切换：
 * 点击执行检查，长按或点右侧箭头切换渠道。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenLanSync: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenMoreSettings: () -> Unit,
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
    val voiceList by vm.voiceList.collectAsState()
    val version = remember { context.appVersion() }

    var picker by remember { mutableStateOf<SettingsPicker?>(null) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var showAllChannelConfirm by remember { mutableStateOf(false) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "设置",
                subtitle = "声音、节点与应用",
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
            SettingSection("朗读") {
                SettingValueRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = "默认声音",
                    subtitle = "点击选择音色与风格",
                    value = if (synth.voice.isBlank()) "节点默认"
                    else voiceDisplayName(voiceList, synth.voice, synth.style),
                    onClick = onOpenVoice,
                    onTrailingClick = onOpenVoice,
                )
                SettingDivider()
                SettingStepperRow(
                    icon = Icons.Rounded.Speed,
                    title = "默认语速",
                    subtitle = "作用于朗读与系统合成",
                    value = ReadTuning.rateLabel(synth.rate),
                    onDecrease = { vm.stepRate(-1) },
                    onIncrease = { vm.stepRate(1) },
                )
                SettingDivider()
                SettingStepperRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "音调",
                    subtitle = "偏高或偏低的声音",
                    value = ReadTuning.pitchLabel(synth.pitch),
                    onDecrease = { vm.stepPitch(-1) },
                    onIncrease = { vm.stepPitch(1) },
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.Bedtime,
                    title = "睡眠定时",
                    subtitle = "到时自动停止播放",
                    value = sleepLabel(appSettings.sleepMinutes),
                    onClick = { picker = SettingsPicker.SLEEP },
                    onTrailingClick = { picker = SettingsPicker.SLEEP },
                )
                SettingDivider()
                SettingSwitchRow(
                    icon = Icons.Rounded.Headphones,
                    title = "后台与锁屏播放",
                    subtitle = "离开应用后继续朗读",
                    checked = appSettings.backgroundPlay,
                    onCheckedChange = { value -> vm.updateApp { it.copy(backgroundPlay = value) } },
                )
                SettingDivider()
                SettingSwitchRow(
                    icon = Icons.Rounded.Animation,
                    title = "自动续听",
                    subtitle = "打开应用时继续上次朗读",
                    checked = appSettings.resume,
                    onCheckedChange = { value -> vm.updateApp { it.copy(resume = value) } },
                )
            }

            SettingSection("节点与调度") {
                SettingValueRow(
                    icon = Icons.Rounded.Dns,
                    title = "节点管理",
                    subtitle = "添加、测试与分组维护",
                    value = "${nodes.size} 个",
                    highlight = true,
                    onClick = onOpenAdmin,
                    onTrailingClick = onOpenAdmin,
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.SwapHoriz,
                    title = "默认模式",
                    subtitle = "自动切换、组轮询或手选节点",
                    value = modeLabel(pool.mode),
                    onClick = { picker = SettingsPicker.MODE },
                    onTrailingClick = { picker = SettingsPicker.MODE },
                )
                SettingDivider()
                SettingStepperRow(
                    icon = Icons.Rounded.Timer,
                    title = "失败冷却",
                    subtitle = "失败节点暂停参与调度",
                    value = "${appSettings.cooldownMinutes} 分钟",
                    onDecrease = { vm.stepCooldown(-1) },
                    onIncrease = { vm.stepCooldown(1) },
                )
                SettingDivider()
                SettingStepperRow(
                    icon = Icons.Rounded.Warning,
                    title = "失败阈值",
                    subtitle = "连续失败多少次后进入冷却",
                    value = "${appSettings.failThreshold} 次",
                    onDecrease = { vm.stepFailThreshold(-1) },
                    onIncrease = { vm.stepFailThreshold(1) },
                )
                SettingDivider()
                SettingSwitchRow(
                    icon = Icons.Rounded.Science,
                    title = "启动时自动测试",
                    subtitle = "进入应用后探测节点健康",
                    checked = appSettings.autoTestOnLaunch,
                    onCheckedChange = { value -> vm.updateApp { it.copy(autoTestOnLaunch = value) } },
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.SwapHoriz,
                    title = "局域网同步",
                    subtitle = "同一 Wi-Fi 下与电脑合并节点",
                    onClick = onOpenLanSync,
                    onTrailingClick = onOpenLanSync,
                )
            }

            SettingSection("外观与权限") {
                SettingValueRow(
                    icon = Icons.Rounded.Palette,
                    title = "外观",
                    subtitle = "主题、动效与触感",
                    value = themeLabel(appSettings.theme),
                    onClick = onOpenAppearance,
                    onTrailingClick = onOpenAppearance,
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.Lock,
                    title = "权限",
                    subtitle = "通知、电池优化与后台运行",
                    value = permissionBadgeText(permissionStates),
                    onClick = onOpenPermissions,
                    onTrailingClick = onOpenPermissions,
                )
            }

            SettingSection("应用") {
                SettingRowLayout(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "检查更新",
                    subtitle = if (updateState.checking) "正在检查更新…"
                    else "当前版本 v$version",
                    onClick = { if (!updateState.checking) updateVm.checkManually() },
                    onLongClick = { showChannelPicker = true },
                    trailing = {
                        SettingValueTrailing(
                            value = channelLabel(appSettings.updateChannel),
                            highlight = true,
                            onClick = { showChannelPicker = true },
                        )
                    },
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.Info,
                    title = "更多设置",
                    subtitle = "服务、数据与应用信息",
                    onClick = onOpenMoreSettings,
                    onTrailingClick = onOpenMoreSettings,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenMoreSettings) {
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

    if (showChannelPicker) {
        SettingPickerSheet(
            title = "更新渠道",
            description = "决定检查更新时看到的版本范围",
            options = listOf(
                SettingSheetOption(
                    id = "stable",
                    label = "正式版",
                    description = "只接收正式发布",
                    selected = appSettings.updateChannel == UpdateChannel.STABLE,
                ),
                SettingSheetOption(
                    id = "all",
                    label = "全部版本",
                    description = "包含测试版构建，可能不稳定",
                    selected = appSettings.updateChannel == UpdateChannel.ALL,
                ),
            ),
            onSelect = { id ->
                showChannelPicker = false
                when {
                    id == "stable" && appSettings.updateChannel != UpdateChannel.STABLE -> {
                        updateVm.setChannel(UpdateChannel.STABLE)
                        onToast("已切换到正式版")
                    }
                    id == "all" && appSettings.updateChannel == UpdateChannel.STABLE -> {
                        showAllChannelConfirm = true
                    }
                }
            },
            onDismiss = { showChannelPicker = false },
        )
    }

    if (showAllChannelConfirm) {
        SettingConfirmSheet(
            title = "切换到全部版本？",
            message = "将同时接收测试版构建",
            note = "测试版可能不稳定或存在缺陷，遇到问题可以随时切回正式版。",
            confirmLabel = "切换",
            onDismiss = { showAllChannelConfirm = false },
            onConfirm = {
                showAllChannelConfirm = false
                updateVm.setChannel(UpdateChannel.ALL)
                onToast("已切换到全部版本，正在检查更新")
            },
        )
    }

    when (picker) {
        SettingsPicker.SLEEP -> SettingPickerSheet(
            title = "睡眠定时",
            description = "到时间后自动停止朗读",
            options = sleepOptions(appSettings.sleepMinutes),
            onSelect = { id ->
                picker = null
                vm.setSleep(id.toIntOrNull() ?: 0)
            },
            onDismiss = { picker = null },
        )
        SettingsPicker.MODE -> SettingPickerSheet(
            title = "默认模式",
            description = "节点调度方式",
            options = listOf(
                SettingSheetOption(
                    id = CallMode.AUTO.name,
                    label = "自动",
                    description = "按健康度与延迟自动选择",
                    selected = pool.mode == CallMode.AUTO,
                ),
                SettingSheetOption(
                    id = CallMode.GROUP.name,
                    label = "组轮询",
                    description = "在分组之间依次轮换",
                    selected = pool.mode == CallMode.GROUP,
                ),
                SettingSheetOption(
                    id = CallMode.MANUAL.name,
                    label = "手选",
                    description = "始终使用手动指定的节点",
                    selected = pool.mode == CallMode.MANUAL,
                ),
            ),
            onSelect = { id ->
                picker = null
                CallMode.entries.firstOrNull { it.name == id }?.let(vm::setMode)
            },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

private fun sleepOptions(current: Int): List<SettingSheetOption> {
    val options = listOf<Triple<Int, String, String?>>(
        Triple(0, "关闭", "不自动停止"),
        Triple(15, "15 分钟", null),
        Triple(30, "30 分钟", null),
        Triple(60, "60 分钟", null),
    )
    return options.map { (minutes, label, hint) ->
        SettingSheetOption(
            id = minutes.toString(),
            label = label,
            description = hint,
            selected = current == minutes,
        )
    }
}

private fun channelLabel(channel: UpdateChannel): String = when (channel) {
    UpdateChannel.STABLE -> "正式版"
    UpdateChannel.ALL -> "全部版本"
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.INK -> "墨水屏"
    ThemeMode.GLASS -> "液态玻璃"
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
internal fun openSystemTtsSettings(context: Context, onToast: (String) -> Unit) {
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
