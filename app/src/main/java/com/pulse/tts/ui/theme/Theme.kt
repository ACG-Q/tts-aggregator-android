package com.pulse.tts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.UiStyle

/** 统一圆角语言：标签 6 / chip 8 / 卡片 12 / 大容器 16 / 弹层 20 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** 液体玻璃 token：标准模式下全部回落为现有配色。 */
@Immutable
data class GlassTokens(
    val enabled: Boolean,
    val cardColor: Color,
    val cardBorder: Color,
    val sheetColor: Color,
    val barColor: Color,
    val backdropBase: Color,
)

val LocalGlass = staticCompositionLocalOf {
    GlassTokens(
        enabled = false,
        cardColor = Color.White,
        cardBorder = Color.Transparent,
        sheetColor = Color.White,
        barColor = Color.White,
        backdropBase = Color.White,
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: UiStyle = UiStyle.STANDARD,
    content: @Composable () -> Unit,
) {
    val glass = remember(darkTheme, style) {
        glassTokens(darkTheme, style == UiStyle.LIQUID_GLASS)
    }
    val base = if (darkTheme) DarkColors else LightColors
    val scheme = if (glass.enabled) base.copy(background = Color.Transparent) else base

    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

private fun glassTokens(dark: Boolean, enabled: Boolean): GlassTokens {
    val base = if (dark) DarkColors else LightColors
    if (!enabled) {
        return GlassTokens(
            enabled = false,
            cardColor = base.surface,
            cardBorder = Color.Transparent,
            sheetColor = base.surface,
            barColor = base.surface,
            backdropBase = base.background,
        )
    }
    return GlassTokens(
        enabled = true,
        cardColor = if (dark) base.surface.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.55f),
        cardBorder = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.75f),
        sheetColor = if (dark) base.surface.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.94f),
        barColor = if (dark) Color(0xFF0D1422).copy(alpha = 0.93f) else Color.White.copy(alpha = 0.65f),
        backdropBase = base.background,
    )
}
