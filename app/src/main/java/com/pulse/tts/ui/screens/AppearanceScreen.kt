package com.pulse.tts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.ThemeSkin
import com.pulse.tts.data.ThemeStyle
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.components.SettingChoiceRow
import com.pulse.tts.ui.components.SettingDivider
import com.pulse.tts.ui.components.SettingSection
import com.pulse.tts.ui.components.SettingSwitchRow
import com.pulse.tts.ui.theme.AppStyle
import com.pulse.tts.ui.theme.DarkColors
import com.pulse.tts.ui.theme.InkDarkColors
import com.pulse.tts.ui.theme.InkLightColors
import com.pulse.tts.ui.theme.LightColors
import com.pulse.tts.ui.theme.LocalAppStyle
import com.pulse.tts.ui.theme.ThemePalette
import com.pulse.tts.ui.theme.resolveTheme
import com.pulse.tts.ui.theme.skinLabel
import com.pulse.tts.ui.theme.styleLabel
import com.pulse.tts.ui.theme.themeSummary
import com.pulse.tts.viewmodel.SettingsViewModel

/**
 * 外观页：样式与主题两个独立维度 + 实时预览。
 *
 * 样式决定明暗（跟随系统 / 浅色 / 深色），主题决定视觉风格（常规 / 墨水屏 / 液态玻璃）。
 * 两个维度可任意组合，颜色与动效的最终结果通过 [resolveTheme] 统一计算。
 * 行规格、分组标题与间距全部复用设置页组件，两个页面保持同一套视觉语言。
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = appViewModel {
        SettingsViewModel(it.nodeRepository, it.settings, it.logStore, it.voiceRepository, it.audioCache)
    }
    val appSettings by vm.appSettings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val style = appSettings.themeStyle
    val skin = appSettings.themeSkin
    val resolved = resolveTheme(style, skin, systemDark)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "外观",
                subtitle = "样式、主题与手感",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SectionLabel("预览")
            PreviewCard(
                palette = resolved.palette,
                title = themeSummary(style, skin),
                subtitle = themeHint(style, skin, systemDark),
            )

            SettingSection("样式") {
                ThemeStyle.entries.forEachIndexed { index, entry ->
                    if (index > 0) SettingDivider()
                    SettingChoiceRow(
                        leading = {
                            Icon(
                                styleIcon(entry),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        title = styleLabel(entry),
                        subtitle = styleHint(entry, systemDark),
                        selected = style == entry,
                        onClick = { vm.updateApp { it.copy(themeStyle = entry) } },
                    )
                }
            }

            SettingSection("主题") {
                ThemeSkin.entries.forEachIndexed { index, entry ->
                    if (index > 0) SettingDivider()
                    SettingChoiceRow(
                        leading = { PaletteSwatch(palette = skinPalette(entry, resolved.dark)) },
                        title = skinLabel(entry),
                        subtitle = skinHint(entry),
                        selected = skin == entry,
                        onClick = { vm.updateApp { it.copy(themeSkin = entry) } },
                    )
                }
            }

            SettingSection("手感") {
                SettingSwitchRow(
                    icon = Icons.Rounded.Animation,
                    title = "减少动态效果",
                    subtitle = "关闭朗读页的脉动与缩放",
                    checked = appSettings.reduceMotion,
                    onCheckedChange = { value -> vm.updateApp { it.copy(reduceMotion = value) } },
                )
                SettingDivider()
                SettingSwitchRow(
                    icon = Icons.Rounded.TouchApp,
                    title = "触感反馈",
                    subtitle = "长按节点时轻微振动",
                    checked = appSettings.haptics,
                    onCheckedChange = { value -> vm.updateApp { it.copy(haptics = value) } },
                )
            }
        }
    }
}

/** 与 [SettingSection] 的标题保持同字重、同缩进 */
@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
    )
}

/**
 * 迷你预览：只展示标题、说明与进度条，避免占满首屏。
 * 背景按主题取渐变或纯色，玻璃主题额外加高光描边。
 */
@Composable
private fun PreviewCard(palette: ThemePalette, title: String, subtitle: String) {
    val colors = paletteColors(palette)
    val style = LocalAppStyle.current.style
    val shape = MaterialTheme.shapes.medium
    val background = when (style) {
        AppStyle.INK -> SolidColor(colors.background)
        AppStyle.STANDARD -> Brush.linearGradient(
            listOf(colors.secondaryContainer.copy(alpha = 0.45f), colors.background),
        )
    }
    val border = when (style) {
        AppStyle.INK -> Modifier.border(1.dp, colors.outlineVariant, shape)
        AppStyle.STANDARD -> Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape)
            .then(border)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(colors.secondary, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surface.copy(alpha = 0.8f),
                ) {
                    Text(
                        "自动调度",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "正在朗读 · 节点 A",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colors.outlineVariant, RoundedCornerShape(999.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.38f)
                        .height(4.dp)
                        .background(colors.primary, RoundedCornerShape(999.dp)),
                )
            }
        }
    }
}

/** 20dp 色板缩略图，尺寸与设置行的图标槽一致 */
@Composable
private fun PaletteSwatch(palette: ThemePalette) {
    val colors = paletteColors(palette)
    val ink = palette == ThemePalette.INK_LIGHT || palette == ThemePalette.INK_DARK
    Box(
        Modifier
            .size(20.dp)
            .background(
                if (ink) {
                    Brush.linearGradient(listOf(colors.surface, colors.background))
                } else {
                    Brush.linearGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.85f),
                            colors.secondary.copy(alpha = 0.6f),
                        ),
                    )
                },
                RoundedCornerShape(6.dp),
            )
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(6.dp)),
    )
}

private fun styleIcon(style: ThemeStyle): ImageVector = when (style) {
    ThemeStyle.SYSTEM -> Icons.Rounded.BrightnessAuto
    ThemeStyle.LIGHT -> Icons.Rounded.LightMode
    ThemeStyle.DARK -> Icons.Rounded.DarkMode
}

private fun styleHint(style: ThemeStyle, systemDark: Boolean): String = when (style) {
    ThemeStyle.SYSTEM -> "当前为${if (systemDark) "深色" else "浅色"}"
    ThemeStyle.LIGHT -> "始终使用明亮界面"
    ThemeStyle.DARK -> "始终使用深色界面"
}

private fun skinHint(skin: ThemeSkin): String = when (skin) {
    ThemeSkin.NORMAL -> "简洁的冷灰背景"
    ThemeSkin.INK -> "单像素描边，自动关闭动效"
}

private fun themeHint(style: ThemeStyle, skin: ThemeSkin, systemDark: Boolean): String = when (skin) {
    ThemeSkin.NORMAL -> if (style.isDark(systemDark)) "常规 · 深色界面" else "常规 · 浅色界面"
    ThemeSkin.INK -> "墨水屏 · 单像素描边，动效已关闭"
}

private fun skinPalette(skin: ThemeSkin, dark: Boolean): ThemePalette = when (skin) {
    ThemeSkin.NORMAL -> if (dark) ThemePalette.DARK else ThemePalette.LIGHT
    ThemeSkin.INK -> if (dark) ThemePalette.INK_DARK else ThemePalette.INK_LIGHT
}

private fun paletteColors(palette: ThemePalette) = when (palette) {
    ThemePalette.LIGHT -> LightColors
    ThemePalette.DARK -> DarkColors
    ThemePalette.INK_LIGHT -> InkLightColors
    ThemePalette.INK_DARK -> InkDarkColors
}
