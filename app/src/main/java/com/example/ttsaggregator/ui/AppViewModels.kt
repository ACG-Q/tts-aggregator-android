package com.example.ttsaggregator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ttsaggregator.AppContainer
import com.example.ttsaggregator.appContainer

/**
 * 依赖注入到 Compose：屏幕通过 appViewModel { ... } 拿到自己的 VM，
 * 不必经过 Activity，也不感知其他屏幕的 VM。
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未提供：请在 setContent 外层调用 ProvideAppContainer")
}

@Composable
fun ProvideAppContainer(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppContainer provides LocalContext.current.appContainer, content = content)
}

/** 声明式获取本屏专属 VM：只传构造函数，工厂样板由这里统一处理 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = remember(container) {
        viewModelFactory { initializer { create(container) } }
    })
}
