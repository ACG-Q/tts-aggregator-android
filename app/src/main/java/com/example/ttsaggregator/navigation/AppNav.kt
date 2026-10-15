package com.example.ttsaggregator.navigation

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
 */
@Composable
fun AppNav(
    onPlay: (ByteArray) -> Unit,
    onToast: (String) -> Unit,
    openSettings: Boolean = false,
) {
    val nav: NavViewModel = viewModel()
    val screen by nav.screen.collectAsState()

    // 通知点击直达设置页（仅首个组合生效，旋转后 savedInstanceState 已置 false）
    LaunchedEffect(openSettings) {
        if (openSettings) nav.goSettings()
    }

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
            onBack = nav::goHome,
            onOpenAdmin = nav::goAdmin,
        )
        is Screen.Voice -> VoiceScreen(
            onBack = nav::goHome,
            onToast = onToast,
        )
        is Screen.Logs -> LogsScreen(
            onBack = nav::goHome,
            onToast = onToast,
        )
        is Screen.Settings -> SettingsScreen(
            onBack = nav::goHome,
            onToast = onToast,
            onOpenPermissions = nav::goPermissions,
            onOpenAdmin = nav::goAdmin,
            onOpenAbout = nav::goAbout,
        )
        is Screen.About -> AboutScreen(
            onBack = nav::goSettings,
            onToast = onToast,
        )
        is Screen.Permissions -> PermissionsScreen(
            onBack = nav::goSettings,
            onToast = onToast,
        )
        is Screen.Admin -> AdminScreen(
            onBack = nav::goSettings,
            onToast = onToast,
        )
    }
}
