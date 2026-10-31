package com.example.ttsaggregator.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Bottom-navigation 的三个一级入口。 */
enum class MainTab { READ, NODES, RECORDS }

/** 应用内页面。Home/Nodes/Logs 是一级 Tab，其余为压栈子页。 */
sealed class Screen {
    object Home : Screen()
    object Nodes : Screen()
    object Logs : Screen()
    object Settings : Screen()
    object Permissions : Screen()
    object Admin : Screen()
    object About : Screen()

    val isMain: Boolean get() = this is Home || this is Nodes || this is Logs
}

/**
 * 纯导航状态：一级 Tab + 子页栈。
 * 子页压栈时记录来源页，返回时回到来源（例如节点页进设置，返回仍是节点页）。
 */
class NavViewModel : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen

    private val _tab = MutableStateFlow(MainTab.READ)
    val tab: StateFlow<MainTab> = _tab

    private val stack = mutableListOf<Screen>()

    fun selectTab(tab: MainTab) {
        _tab.value = tab
        _screen.value = when (tab) {
            MainTab.READ -> Screen.Home
            MainTab.NODES -> Screen.Nodes
            MainTab.RECORDS -> Screen.Logs
        }
        stack.clear()
    }

    private fun push(screen: Screen) {
        stack.add(_screen.value)
        _screen.value = screen
    }

    /** 返回：子页出栈；一级 Tab 回到朗读；朗读页返回 false（交给系统 finish）。 */
    fun handleBack(): Boolean {
        if (stack.isNotEmpty()) {
            _screen.value = stack.removeLast()
            _tab.value = _screen.value.tabOf()
            return true
        }
        return when (_screen.value) {
            is Screen.Nodes, is Screen.Logs -> {
                selectTab(MainTab.READ)
                true
            }
            else -> false
        }
    }

    fun goHome() = selectTab(MainTab.READ)
    fun goNodes() = selectTab(MainTab.NODES)
    fun goLogs() = selectTab(MainTab.RECORDS)
    fun goSettings() = push(Screen.Settings)
    fun goPermissions() = push(Screen.Permissions)
    fun goAdmin() = push(Screen.Admin)
    fun goAbout() = push(Screen.About)

    private fun Screen.tabOf(): MainTab = when (this) {
        is Screen.Nodes -> MainTab.NODES
        is Screen.Logs -> MainTab.RECORDS
        else -> MainTab.READ
    }
}
