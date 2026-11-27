package com.pulse.tts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.pulse.tts.ui.theme.LocalAppStyle

/**
 * 主题化页面背景：标准为主题渐变，墨水屏为纯色。
 * 调用方无需自己叠加背景色。
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val tokens = LocalAppStyle.current
    val colors = MaterialTheme.colorScheme
    val base = modifier.fillMaxSize()
    when {
        tokens.useGradients -> Box(
            base.background(
                Brush.verticalGradient(
                    listOf(
                        colors.secondaryContainer.copy(alpha = 0.42f),
                        colors.background,
                        colors.primaryContainer.copy(alpha = 0.55f),
                    ),
                ),
            ),
        )
        else -> Box(base.background(colors.background))
    }
}
