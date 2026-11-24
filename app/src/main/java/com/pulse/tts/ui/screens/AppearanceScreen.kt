package com.pulse.tts.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.ThemeMode
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.theme.DarkColors
import com.pulse.tts.ui.theme.GlassDarkColors
import com.pulse.tts.ui.theme.GlassLightColors
import com.pulse.tts.ui.theme.InkDarkColors
import com.pulse.tts.ui.theme.InkLightColors
import com.pulse.tts.ui.theme.LightColors
import com.pulse.tts.ui.theme.ThemePalette
import com.pulse.tts.ui.theme.resolveTheme
import com.pulse.tts.viewmodel.SettingsViewModel

/**
 * 外观页：主题选择 + 实时预览。
 *
 * 跟随系统 / 浅色 / 深色 / 墨水屏 / 液态玻璃 五种模式，
 * 墨水屏与玻璃各自带明暗两套色板，明暗跟随系统深浅。
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = appViewModel {
        SettingsViewModel(it.nodeRepository, it.settings, it.logStore, it.voiceRepository)
    }
    val appSettings by vm.appSettings.collectAsState()
    val systemDark = isSystemInDarkTheme()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "外观",
                subtitle = "主题、动效与触感",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreviewCard(
                palette = resolveTheme(appSettings.theme, systemDark).palette,
                title = themeLabel(appSettings.theme),
                subtitle = themeHint(appSettings.theme, systemDark),
            )

            ThemeMode.entries.forEach { mode ->
                ThemeRow(
                    mode = mode,
                    selected = appSettings.theme == mode,
                    systemDark = systemDark,
                    onClick = { vm.updateApp { it.copy(theme = mode) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "手感",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
            ToggleRow(
                title = "减少动态效果",
                subtitle = "关闭朗读页的脉动与缩放",
                checked = appSettings.reduceMotion,
                onCheckedChange = { value -> vm.updateApp { it.copy(reduceMotion = value) } },
            )
            ToggleRow(
                title = "触感反馈",
                subtitle = "长按节点时轻微振动",
                checked = appSettings.haptics,
                onCheckedChange = { value -> vm.updateApp { it.copy(haptics = value) } },
            )
        }
    }
}

@Composable
private fun ThemeRow(
    mode: ThemeMode,
    selected: Boolean,
    systemDark: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaletteSwatch(resolveTheme(mode, systemDark).palette, Modifier.size(44.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(themeLabel(mode), style = MaterialTheme.typography.titleSmall)
                Text(
                    themeHint(mode, systemDark),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PreviewCard(palette: ThemePalette, title: String, subtitle: String) {
    val colors = paletteColors(palette)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colors.secondaryContainer, colors.background, colors.primaryContainer),
                    ),
                )
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colors.secondary, CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onBackground,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "正在朗读 · 节点 A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Text(
                        "本机文本 · 自动调度",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(palette: ThemePalette, modifier: Modifier = Modifier) {
    val colors = paletteColors(palette)
    val ink = palette == ThemePalette.INK_LIGHT || palette == ThemePalette.INK_DARK
    Box(
        modifier
            .background(colors.background, RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (ink) {
                        Brush.linearGradient(listOf(colors.surface, colors.background))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.75f),
                                colors.secondary.copy(alpha = 0.45f),
                                colors.surface,
                            ),
                        )
                    },
                    RoundedCornerShape(7.dp),
                ),
        )
    }
}

private fun paletteColors(palette: ThemePalette) = when (palette) {
    ThemePalette.LIGHT -> LightColors
    ThemePalette.DARK -> DarkColors
    ThemePalette.INK_LIGHT -> InkLightColors
    ThemePalette.INK_DARK -> InkDarkColors
    ThemePalette.GLASS_LIGHT -> GlassLightColors
    ThemePalette.GLASS_DARK -> GlassDarkColors
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.INK -> "墨水屏"
    ThemeMode.GLASS -> "液态玻璃"
}

private fun themeHint(mode: ThemeMode, systemDark: Boolean): String = when (mode) {
    ThemeMode.SYSTEM -> "当前为${if (systemDark) "深色" else "浅色"}"
    ThemeMode.LIGHT -> "明亮冷灰底"
    ThemeMode.DARK -> "OLED 深蓝黑"
    ThemeMode.INK -> "自动关闭动效，状态用文字与图标区分"
    ThemeMode.GLASS -> "关键页面应用分层玻璃"
}
