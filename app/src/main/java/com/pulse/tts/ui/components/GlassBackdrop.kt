package com.pulse.tts.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pulse.tts.ui.theme.LocalGlass

/** 玻璃模式底图：基础渐变 + 两团低透明度光斑，不做真实模糊。 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val top = lerp(glass.backdropBase, primary, 0.05f)
    val bottom = lerp(glass.backdropBase, secondary, 0.04f)

    Canvas(modifier = modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(top, glass.backdropBase, bottom)))

        val primaryCenter = Offset(size.width * 0.12f, size.height * 0.06f)
        val primaryRadius = size.minDimension * 0.62f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.16f), Color.Transparent),
                center = primaryCenter,
                radius = primaryRadius,
            ),
            radius = primaryRadius,
            center = primaryCenter,
        )

        val secondaryCenter = Offset(size.width * 0.92f, size.height * 0.72f)
        val secondaryRadius = size.minDimension * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.13f), Color.Transparent),
                center = secondaryCenter,
                radius = secondaryRadius,
            ),
            radius = secondaryRadius,
            center = secondaryCenter,
        )
    }
}
