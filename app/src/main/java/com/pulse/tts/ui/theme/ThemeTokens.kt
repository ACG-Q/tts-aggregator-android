package com.pulse.tts.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.ThemeMode

/** 视觉风格：标准、墨水屏、液态玻璃。 */
internal enum class AppStyle { STANDARD, INK, GLASS }

/** 具体色板；墨水屏与玻璃各自带明暗两套，跟随系统深浅切换。 */
internal enum class ThemePalette { LIGHT, DARK, INK_LIGHT, INK_DARK, GLASS_LIGHT, GLASS_DARK }

/** 主题模式的解析结果：风格 + 实际深浅 + 色板。 */
internal data class ResolvedTheme(
    val style: AppStyle,
    val dark: Boolean,
    val palette: ThemePalette,
)

internal fun resolveTheme(mode: ThemeMode, systemDark: Boolean): ResolvedTheme = when (mode) {
    ThemeMode.SYSTEM -> ResolvedTheme(
        AppStyle.STANDARD,
        systemDark,
        if (systemDark) ThemePalette.DARK else ThemePalette.LIGHT,
    )
    ThemeMode.LIGHT -> ResolvedTheme(AppStyle.STANDARD, false, ThemePalette.LIGHT)
    ThemeMode.DARK -> ResolvedTheme(AppStyle.STANDARD, true, ThemePalette.DARK)
    ThemeMode.INK -> ResolvedTheme(
        AppStyle.INK,
        systemDark,
        if (systemDark) ThemePalette.INK_DARK else ThemePalette.INK_LIGHT,
    )
    ThemeMode.GLASS -> ResolvedTheme(
        AppStyle.GLASS,
        systemDark,
        if (systemDark) ThemePalette.GLASS_DARK else ThemePalette.GLASS_LIGHT,
    )
}

/** 风格 token：动效、渐变、阴影、描边与状态色表现。 */
@Immutable
internal data class AppStyleTokens(
    val style: AppStyle,
    val motionEnabled: Boolean,
    val useGradients: Boolean,
    val useShadows: Boolean,
    val cardBorderWidth: Dp,
    val statusUsesColor: Boolean,
)

internal val StandardStyleTokens = AppStyleTokens(
    style = AppStyle.STANDARD,
    motionEnabled = true,
    useGradients = true,
    useShadows = true,
    cardBorderWidth = 0.dp,
    statusUsesColor = true,
)

internal val InkStyleTokens = AppStyleTokens(
    style = AppStyle.INK,
    motionEnabled = false,
    useGradients = false,
    useShadows = false,
    cardBorderWidth = 1.dp,
    statusUsesColor = false,
)

internal val GlassStyleTokens = AppStyleTokens(
    style = AppStyle.GLASS,
    motionEnabled = true,
    useGradients = true,
    useShadows = true,
    cardBorderWidth = 0.dp,
    statusUsesColor = true,
)

/** 状态色：常规主题用固定语义色，墨水屏退化为灰度。 */
@Immutable
internal data class StatusPalette(
    val ok: Color,
    val error: Color,
    val warn: Color,
    val muted: Color,
    val info: Color,
    val accent: Color,
)

internal val LocalAppStyle = staticCompositionLocalOf { StandardStyleTokens }

internal val LocalStatusColors = staticCompositionLocalOf {
    StatusPalette(
        ok = StatusColors.Ok,
        error = StatusColors.Error,
        warn = StatusColors.Warn,
        muted = StatusColors.Muted,
        info = StatusColors.Info,
        accent = StatusColors.Accent,
    )
}
