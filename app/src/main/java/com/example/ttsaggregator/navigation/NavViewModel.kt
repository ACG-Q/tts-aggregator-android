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
 * 屏幕之间用 go* 方法跳转，返回路径显式声明在各 go 方法里。
 */
class NavViewModel : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen

    fun goHome() { _screen.value = Screen.Home }
    fun goNodes() { _screen.value = Screen.Nodes }
    fun goVoice() { _screen.value = Screen.Voice }
    fun goLogs() { _screen.value = Screen.Logs }
    fun goSettings() { _screen.value = Screen.Settings }
    fun goPermissions() { _screen.value = Screen.Permissions }
    fun goAdmin() { _screen.value = Screen.Admin }
    fun goAbout() { _screen.value = Screen.About }
}
