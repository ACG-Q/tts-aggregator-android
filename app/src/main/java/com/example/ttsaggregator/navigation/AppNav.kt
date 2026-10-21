package com.example.ttsaggregator.navigation

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ttsaggregator.ui.screens.AboutScreen
import com.example.ttsaggregator.ui.screens.AdminScreen
import com.example.ttsaggregator.ui.screens.HomeScreen
import com.example.ttsaggregator.ui.screens.LogsScreen
import com.example.ttsaggregator.ui.screens.NodesScreen
import com.example.ttsaggregator.ui.screens.PermissionsScreen
import com.example.ttsaggregator.ui.screens.SettingsScreen
import com.example.ttsaggregator.ui.screens.VoiceScreen

/**
 * 导航图：唯一的屏幕切换入口。
 * 每屏只接收自己需要的回调（返回 / 跳转 / 播放 / Toast），互不感知。
 *
 * 系统返回键（硬件键 / 手势）：通过 OnBackPressedDispatcher 拦截，
 * 调 NavViewModel.handleBack() 弹栈；栈空时放行（finish Activity）。
 */
@Composable
fun AppNav(
    onPlay: (ByteArray) -> Unit,
    onToast: (String) -> Unit,
) {
    val nav: NavViewModel = viewModel()
    val screen by nav.screen.collectAsState()

    // 系统返回键：仅在有上级页面时拦截弹栈；首页放行（finish Activity）
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
    LaunchedEffect(backCallback, screen) {
        backCallback?.isEnabled = screen !is Screen.Home
    }

    val onBack: () -> Unit = { nav.handleBack() }

    when (screen) {
        is Screen.Home -> HomeScreen(
            onPlay = onPlay,
            onToast = onToast,
            onOpenSettings = nav::goSettings,
            onOpenVoice = nav::goVoice,
            onOpenNodes = nav::goNodes,
            onOpenLogs = nav::goLogs,
        )
        is Screen.Nodes -> NodesScreen(
            onBack = onBack,
            onOpenAdmin = nav::goAdmin,
        )
        is Screen.Voice -> VoiceScreen(
            onBack = onBack,
            onToast = onToast,
        )
        is Screen.Logs -> LogsScreen(
            onBack = onBack,
            onToast = onToast,
        )
        is Screen.Settings -> SettingsScreen(
            onBack = onBack,
            onOpenPermissions = nav::goPermissions,
            onOpenAdmin = nav::goAdmin,
            onOpenAbout = nav::goAbout,
            onToast = onToast,
        )
        is Screen.About -> AboutScreen(
            onBack = onBack,
            onToast = onToast,
        )
        is Screen.Permissions -> PermissionsScreen(
            onBack = onBack,
            onToast = onToast,
        )
        is Screen.Admin -> AdminScreen(
            onBack = onBack,
            onToast = onToast,
        )
    }
}
