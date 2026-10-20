package com.example.ttsaggregator.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 应用内页面（7 屏） */
sealed class Screen {
    object Home : Screen()       // 首页：朗读 + 调用逻辑 + 调度 + 日志简版
    object Nodes : Screen()      // 节点列表（调用逻辑的子页）
    object Voice : Screen()      // 语音设置（首页子页）
    object Logs : Screen()       // 调用日志详情
    object Settings : Screen()   // 设置（权限/节点/关于）
    object Permissions : Screen()
    object Admin : Screen()      // 节点管理
    object About : Screen()      // 关于（zuoban/tts 项目信息）
}

/**
 * 纯导航状态：只管"当前在哪一屏"，不持有任何业务数据。
 * 屏幕之间用 go* 方法跳转（同步压栈），handleBack 弹栈；
 * 栈空时返回 false，由调用方（OnBackPressedDispatcher）决定是否 finish Activity。
 */
class NavViewModel : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen

    private var stack = mutableListOf<Screen>()

    private fun push(screen: Screen) {
        _screen.value = screen
        stack.add(screen)
    }

    /** 弹栈返回；栈已空（停在 Home）返回 false。 */
    fun handleBack(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeLast()
        _screen.value = stack.lastOrNull() ?: Screen.Home
        return true
    }

    fun goHome() { _screen.value = Screen.Home; stack = mutableListOf() }
    fun goNodes() = push(Screen.Nodes)
    fun goVoice() = push(Screen.Voice)
    fun goLogs() = push(Screen.Logs)
    fun goSettings() = push(Screen.Settings)
    fun goPermissions() = push(Screen.Permissions)
    fun goAdmin() = push(Screen.Admin)
    fun goAbout() = push(Screen.About)
}
