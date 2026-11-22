package com.pulse.tts.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 通知点击到 UI 的进程内信号；token 自增，Compose 侧按值变化触发。 */
internal object SystemReadingEntry {

    private val _openToken = MutableStateFlow(0L)
    val openToken: StateFlow<Long> = _openToken.asStateFlow()

    fun requestOpen() {
        _openToken.update { it + 1 }
    }
}
