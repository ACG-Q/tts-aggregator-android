package com.pulse.tts.navigation

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.pulse.tts.ui.screens.AdminScreen
import com.pulse.tts.ui.screens.HomeScreen
import com.pulse.tts.ui.screens.LogsScreen
import com.pulse.tts.ui.screens.NodesScreen
import com.pulse.tts.ui.screens.PermissionsScreen
import com.pulse.tts.ui.screens.ReadingScreen
import com.pulse.tts.ui.screens.SettingsScreen
import com.pulse.tts.ui.screens.picker.VoicePickerSheet

/**
 * 应用壳：一级 Tab（朗读 / 节点 / 记录）+ 子页栈 + 全屏朗读态。
 * 子页复用原有页面，返回键与底部导航由这里统一接管。
 */
@Composable
fun AppNav(
    player: PlaybackController,
    onToast: (String) -> Unit,
) {
    val nav: NavViewModel = viewModel()
    val screen by nav.screen.collectAsState()
    val tab by nav.tab.collectAsState()
    val playback by player.state.collectAsState()
    var readerVisible by remember { mutableStateOf(false) }
    var voicePickerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(playback.isActive) {
        if (!playback.isActive) readerVisible = false
    }

    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backCallback = remember(dispatcher) {
        dispatcher?.let { d ->
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
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
    LaunchedEffect(backCallback, screen, readerVisible) {
        backCallback?.isEnabled = !screen.isMain || screen is Screen.Nodes || screen is Screen.Logs || readerVisible
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
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
            )
            is Screen.Settings -> SettingsScreen(
                onBack = { nav.handleBack() },
                onOpenPermissions = nav::goPermissions,
                onOpenAdmin = nav::goAdmin,
                onOpenAbout = nav::goAbout,
                onOpenVoice = { voicePickerVisible = true },
                onToast = onToast,
            )
            is Screen.About -> AboutScreen(
                onBack = { nav.handleBack() },
                onToast = onToast,
            )
            is Screen.Permissions -> PermissionsScreen(
                onBack = { nav.handleBack() },
                onToast = onToast,
            )
            is Screen.Admin -> AdminScreen(
                onBack = { nav.handleBack() },
                onToast = onToast,
            )
            else -> Unit
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
    }
}
