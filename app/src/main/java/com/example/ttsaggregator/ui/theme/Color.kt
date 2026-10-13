package com.example.ttsaggregator.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 全新配色：冷静的钴蓝主色 + 石墨中性色 + 青/琥珀点缀。
 * 浅色背景带轻微冷灰，卡片纯白描边扁平化；深色为 OLED 友好的深石墨。
 */

// ---------- 语义色（不随主题变化的状态色） ----------

object StatusColors {
    val Ok = Color(0xFF1F9D55)       // 健康 / 成功
    val Error = Color(0xFFDC3A41)    // 异常 / 失败
    val Warn = Color(0xFFD98E04)     // 冷却 / 警告
    val Muted = Color(0xFF8A93A2)    // 未测试 / 次要
    val Info = Color(0xFF2C6BED)     // 免Key / 信息
    val Accent = Color(0xFF7C4DDB)   // 新版V1
}

// ---------- 浅色 ----------

internal val LightColors = lightColorScheme(
    primary = Color(0xFF3159D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE4FF),
    onPrimaryContainer = Color(0xFF0C1E66),
    secondary = Color(0xFF0E8C7F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9F2EC),
    onSecondaryContainer = Color(0xFF002E29),
    tertiary = Color(0xFF9A6100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEB8),
    onTertiaryContainer = Color(0xFF311400),
    error = StatusColors.Error,
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE4E7EE),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC5C8D0),
    outlineVariant = Color(0xFFE1E4EA),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFF3F5F9),
    surfaceContainerHigh = Color(0xFFEDEFF4),
)

// ---------- 深色 ----------

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C3FF),
    onPrimary = Color(0xFF0C2375),
    primaryContainer = Color(0xFF2841A8),
    onPrimaryContainer = Color(0xFFDEE4FF),
    secondary = Color(0xFF80D6C9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFC9F2EC),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF503000),
    tertiaryContainer = Color(0xFF724800),
    onTertiaryContainer = Color(0xFFFFDEB8),
    error = Color(0xFFFFB3AD),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF191C22),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CE),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF2A2D35),
    surfaceContainerLow = Color(0xFF15181D),
    surfaceContainer = Color(0xFF191C22),
    surfaceContainerHigh = Color(0xFF24272E),
)
