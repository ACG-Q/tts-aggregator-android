package com.pulse.tts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.ThemeMode

/** 统一圆角语言：标签 6 / chip 8 / 卡片 12 / 大容器 16 / 弹层 20 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** 墨水屏：极小圆角 + 单像素描边语言 */
val InkShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

/** 液态玻璃：胶囊与大圆角叠层 */
val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * 主题入口：把 [ThemeMode] 解析成色板、形状与风格 token。
 *
 * 风格 token 通过 [LocalAppStyle] 下发，状态色通过 [LocalStatusColors] 下发，
 * 供卡片、底栏、朗读页等组件按主题调整描边、渐变、阴影与动效。
 */
@Composable
fun AppTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    systemDark: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val resolved = resolveTheme(mode, systemDark)
    val colors = when (resolved.palette) {
        ThemePalette.LIGHT -> LightColors
        ThemePalette.DARK -> DarkColors
        ThemePalette.INK_LIGHT -> InkLightColors
        ThemePalette.INK_DARK -> InkDarkColors
        ThemePalette.GLASS_LIGHT -> GlassLightColors
        ThemePalette.GLASS_DARK -> GlassDarkColors
    }
    val shapes = when (resolved.style) {
        AppStyle.STANDARD -> AppShapes
        AppStyle.INK -> InkShapes
        AppStyle.GLASS -> GlassShapes
    }
    val tokens = when (resolved.style) {
        AppStyle.STANDARD -> StandardStyleTokens
        AppStyle.INK -> InkStyleTokens
        AppStyle.GLASS -> GlassStyleTokens
    }.let { it.copy(motionEnabled = it.motionEnabled && !reduceMotion) }

    val status = if (resolved.style == AppStyle.INK) {
        StatusPalette(
            ok = colors.onSurface,
            error = colors.onSurface,
            warn = colors.onSurfaceVariant,
            muted = colors.outline,
            info = colors.onSurfaceVariant,
            accent = colors.onSurface,
        )
    } else {
        StatusPalette(
            ok = StatusColors.Ok,
            error = StatusColors.Error,
            warn = StatusColors.Warn,
            muted = StatusColors.Muted,
            info = StatusColors.Info,
            accent = StatusColors.Accent,
        )
    }

    CompositionLocalProvider(
        LocalAppStyle provides tokens,
        LocalStatusColors provides status,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = shapes,
            content = content,
        )
    }
}
