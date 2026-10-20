package com.example.ttsaggregator.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    // 系统返回键：栈非空时拦截并弹栈；栈空时放行（finish Activity）
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(dispatcher) {
        val d = dispatcher ?: return@LaunchedEffect
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!nav.handleBack()) {
                    isEnabled = false
                    d.onBackPressed()
                }
            }
        }
        d.addCallback(callback)
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
