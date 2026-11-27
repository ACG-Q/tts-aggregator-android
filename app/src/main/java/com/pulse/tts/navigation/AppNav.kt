package com.pulse.tts.navigation

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.tts.playback.PlaybackController
import com.pulse.tts.ui.components.PulseTabBar
import com.pulse.tts.ui.screens.AboutScreen
import com.pulse.tts.ui.screens.AppearanceScreen
import com.pulse.tts.ui.screens.HomeScreen
import com.pulse.tts.ui.screens.LogDetailScreen
import com.pulse.tts.ui.screens.LogsScreen
import com.pulse.tts.ui.screens.MoreSettingsScreen
import com.pulse.tts.ui.screens.NodesScreen
import com.pulse.tts.ui.screens.PermissionsScreen
import com.pulse.tts.ui.screens.ReadingScreen
import com.pulse.tts.ui.screens.SettingsScreen
import com.pulse.tts.ui.screens.SystemReaderScreen
import com.pulse.tts.ui.screens.UpdateHost
import com.pulse.tts.tts.SystemReadingEntry
import com.pulse.tts.data.lansync.LanSyncEntry
import com.pulse.tts.ui.screens.picker.VoicePickerSheet
import com.pulse.tts.ui.screens.LanSyncScreen
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.viewmodel.UpdateViewModel
import com.pulse.tts.ui.theme.LocalMotion

/**
 * 应用壳：一级 Tab（朗读 / 节点 / 记录）+ 子页栈 + 全屏朗读态。
 * 子页复用原有页面，返回键与底部导航由这里统一接管。
 */
@Composable
fun AppNav(
    player: PlaybackController,
    onToast: (String) -> Unit,
    onExitApp: () -> Unit,
) {
    val nav: NavViewModel = viewModel()
    val updateVm: UpdateViewModel = appViewModel { UpdateViewModel(it.updateRepository) }
    val screen by nav.screen.collectAsState()
    val tab by nav.tab.collectAsState()
    val playback by player.state.collectAsState()
    var readerVisible by remember { mutableStateOf(false) }
    var systemReaderVisible by remember { mutableStateOf(false) }
    var voicePickerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(playback.isActive) {
        if (!playback.isActive) readerVisible = false
    }

    val readerOpenToken by SystemReadingEntry.openToken.collectAsState()
    LaunchedEffect(readerOpenToken) {
        if (readerOpenToken > 0) systemReaderVisible = true
    }

    LaunchedEffect(Unit) {
        LanSyncEntry.pairing.collect { raw ->
            if (raw != null) nav.goLanSync()
        }
    }

    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backCallback = remember(dispatcher) {
        dispatcher?.let { d ->
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (systemReaderVisible) {
                        systemReaderVisible = false
                        return
                    }
                    if (!nav.handleBack()) {
                        isEnabled = false
                        d.onBackPressed()
                    }
                }
            }
        }
    }
    DisposableEffect(dispatcher, backCallback) {
        backCallback?.let { dispatcher?.addCallback(it) }
        onDispose { backCallback?.remove() }
    }
    LaunchedEffect(backCallback, screen, readerVisible, systemReaderVisible) {
        backCallback?.isEnabled = !screen.isMain ||
            screen is Screen.Nodes ||
            screen is Screen.Logs ||
            readerVisible ||
            systemReaderVisible
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val motion = LocalMotion.current
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(tween(motion.duration(180))) togetherWith
                    fadeOut(tween(motion.duration(180)))
            },
            label = "appScreen",
        ) { current ->
            when (current) {
            is Screen.Home -> HomeScreen(
                player = player,
                onToast = onToast,
                onOpenSettings = nav::goSettings,
                onOpenPermissions = nav::goPermissions,
                onOpenVoice = { voicePickerVisible = true },
                onOpenReader = { readerVisible = true },
            )
            is Screen.Nodes -> NodesScreen(
                onToast = onToast,
            )
            is Screen.Logs -> LogsScreen(
                onToast = onToast,
                onOpenDetail = nav::goLogDetail,
            )
            is Screen.LogDetail -> LogDetailScreen(
                log = current.log,
                onBack = { nav.handleBack() },
                onToast = onToast,
            )
            is Screen.Settings -> SettingsScreen(
                onBack = { nav.handleBack() },
                onOpenPermissions = nav::goPermissions,
                onOpenVoice = { voicePickerVisible = true },
                onOpenLanSync = nav::goLanSync,
                onOpenAppearance = nav::goAppearance,
                onOpenMoreSettings = nav::goMoreSettings,
                onToast = onToast,
            )
            is Screen.MoreSettings -> MoreSettingsScreen(
                onBack = { nav.handleBack() },
                onOpenAbout = nav::goAbout,
                onToast = onToast,
            )
            is Screen.Appearance -> AppearanceScreen(
                onBack = { nav.handleBack() },
            )
            is Screen.LanSync -> LanSyncScreen(onBack = { nav.handleBack() })
            is Screen.About -> AboutScreen(
                onBack = { nav.handleBack() },
                onToast = onToast,
            )
            is Screen.Permissions -> PermissionsScreen(
                onBack = { nav.handleBack() },
                onToast = onToast,
            )
            else -> Unit
            }
        }

        if (screen.isMain) {
            PulseTabBar(
                selected = tab,
                onSelect = nav::selectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (readerVisible && playback.isActive) {
            ReadingScreen(
                controller = player,
                onCollapse = { readerVisible = false },
            )
        }

        if (systemReaderVisible) {
            SystemReaderScreen(
                onCollapse = { systemReaderVisible = false },
                onOpenLogs = {
                    systemReaderVisible = false
                    nav.goLogs()
                },
            )
        }

        if (voicePickerVisible) {
            VoicePickerSheet(
                onDismiss = { voicePickerVisible = false },
                onApplied = { message ->
                    voicePickerVisible = false
                    onToast(message)
                },
                onToast = onToast,
            )
        }

        UpdateHost(
            vm = updateVm,
            onToast = onToast,
            onExitApp = onExitApp,
        )
    }
}
