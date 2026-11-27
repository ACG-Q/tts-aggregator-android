package com.pulse.tts.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 全局触感入口：设置页的「触感反馈」只控制这里。
 *
 * 关闭时所有方法都是空操作，调用方不必再判断开关。
 */
@Immutable
class AppHaptics(
    private val enabled: Boolean,
    private val feedback: HapticFeedback?,
) {
    /** 轻触：进入子页、普通按钮、确认。 */
    fun tap() = play(HapticFeedbackType.TextHandleMove)

    /** 切换：开关、单选、标签页、多选。 */
    fun toggle() = play(HapticFeedbackType.TextHandleMove)

    /** 长按：唤出次要操作或进入多选。 */
    fun longPress() = play(HapticFeedbackType.LongPress)

    private fun play(type: HapticFeedbackType) {
        if (enabled) feedback?.performHapticFeedback(type)
    }
}

val LocalAppHaptics = compositionLocalOf { AppHaptics(enabled = false, feedback = null) }

/** 在 AppTheme 内包一层，把设置里的开关接到所有共享组件。 */
@Composable
fun ProvideAppHaptics(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val feedback = LocalHapticFeedback.current
    val haptics = remember(enabled, feedback) { AppHaptics(enabled, feedback) }
    CompositionLocalProvider(LocalAppHaptics provides haptics, content = content)
}
