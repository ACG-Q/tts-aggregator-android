package com.pulse.tts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.pulse.tts.ui.theme.AppStyle
import com.pulse.tts.ui.theme.LocalAppStyle

/**
 * 主题化页面背景：玻璃为多层渐变，标准为主题渐变，墨水屏为纯色。
 * 调用方无需自己叠加背景色。
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val tokens = LocalAppStyle.current
    val colors = MaterialTheme.colorScheme
    val base = modifier.fillMaxSize()
    when {
        tokens.style == AppStyle.GLASS -> Box(
            base.background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.primaryContainer.copy(alpha = 0.55f),
                        colors.secondaryContainer.copy(alpha = 0.35f),
                        colors.background,
                    ),
                ),
            ),
        )
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

/** 玻璃风格的渐变高光描边；其它风格原样返回。 */
@Composable
fun Modifier.glassBorder(shape: Shape): Modifier {
    if (LocalAppStyle.current.style != AppStyle.GLASS) return this
    return border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.55f),
            ),
        ),
        shape = shape,
    )
}
