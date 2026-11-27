package com.pulse.tts.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/** 动画开关：墨水屏与「减少动态效果」都会把它关掉。 */
@Immutable
class MotionSpec(val enabled: Boolean) {
    /** 关闭时返回 0，动画立即结束。 */
    fun duration(baseMillis: Int): Int = if (enabled) baseMillis else 0
}

val LocalMotion = compositionLocalOf { MotionSpec(enabled = true) }
