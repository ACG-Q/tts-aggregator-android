package com.pulse.tts.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Pulse 设计 token：浅色冷灰底 + 墨蓝文字 + 电光蓝主色，深色为 OLED 深蓝黑。
 * 状态色保持固定语义，不随主题反转。
 */

object StatusColors {
    val Ok = Color(0xFF1F9D55)
    val Error = Color(0xFFDC3A41)
    val Warn = Color(0xFFC77700)
    val Muted = Color(0xFF8A93A2)
    val Info = Color(0xFF2C6BED)
    val Accent = Color(0xFF0E8C7F)
}

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2457E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7EEFF),
    onPrimaryContainer = Color(0xFF0B1F4D),
    secondary = Color(0xFF008F88),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDF6F2),
    onSecondaryContainer = Color(0xFF00342F),
    tertiary = Color(0xFFA84B06),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF0D9),
    onTertiaryContainer = Color(0xFF3A1A00),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEECEA),
    onErrorContainer = Color(0xFF4A0F0B),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFF5B6B82),
    outline = Color(0xFFC8D2E1),
    outlineVariant = Color(0xFFE2E8F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFF4F6FB),
    surfaceContainerHigh = Color(0xFFEDF1F8),
    surfaceContainerHighest = Color(0xFFE2E8F2),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF07111F),
    primaryContainer = Color(0xFF16294A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF4FE0C8),
    onSecondary = Color(0xFF04231F),
    secondaryContainer = Color(0xFF0E2E2B),
    onSecondaryContainer = Color(0xFFB8F5EA),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF33220E),
    tertiaryContainer = Color(0xFF33220E),
    onTertiaryContainer = Color(0xFFFFDCB4),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF2B0A08),
    errorContainer = Color(0xFF331614),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF080D18),
    onBackground = Color(0xFFF2F6FC),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF2F6FC),
    surfaceVariant = Color(0xFF182235),
    onSurfaceVariant = Color(0xFF9AA9BD),
    outline = Color(0xFF334564),
    outlineVariant = Color(0xFF223049),
    surfaceContainerLowest = Color(0xFF080D18),
    surfaceContainerLow = Color(0xFF0D1422),
    surfaceContainer = Color(0xFF111827),
    surfaceContainerHigh = Color(0xFF182235),
    surfaceContainerHighest = Color(0xFF22304A),
)
