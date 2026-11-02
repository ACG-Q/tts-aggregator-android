package com.example.ttsaggregator.ui.screens.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.ui.util.Labels

/** 顶部快捷筛选：常用 → 全部 → 中文 → 英语 → 其余语言，按可用宽度动态排布。 */
@Composable
internal fun VoiceFilterChips(
    languages: List<Pair<String, Int>>,
    filter: VoiceFilter,
    onFilter: (VoiceFilter) -> Unit,
    onMoreLanguages: () -> Unit,
) {
    val entries = remember(languages) { chipEntries(languages) }
    val chipStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 2.dp, bottom = 10.dp),
    ) {
        val spacing = with(density) { 7.dp.toPx() }
        val horizontalPadding = with(density) { 24.dp.toPx() }
        val widths = entries.map {
            measurer.measure(it.label, chipStyle).size.width + horizontalPadding
        }
        val moreWidth = measurer.measure("更多", chipStyle).size.width + horizontalPadding
        val plan = remember(widths, constraints.maxWidth, spacing, moreWidth) {
            planChips(widths, constraints.maxWidth.toFloat(), spacing, moreWidth)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            entries.take(plan.visibleCount).forEach { entry ->
                PickerChip(
                    text = entry.label,
                    selected = chipSelected(filter, entry.filter),
                    onClick = { onFilter(entry.filter) },
                )
            }
            if (plan.overflow) {
                PickerChip("更多", false, onMoreLanguages)
            }
        }
    }
}

private fun chipSelected(current: VoiceFilter, entry: VoiceFilter): Boolean = when (entry) {
    VoiceFilter.Frequent -> current == VoiceFilter.Frequent
    VoiceFilter.Chinese -> current == VoiceFilter.Chinese
    VoiceFilter.All -> current == VoiceFilter.All
    is VoiceFilter.Language -> current is VoiceFilter.Language && current.code == entry.code
}

@Composable
private fun PickerChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** 完整语言网格，中文第一。 */
@Composable
internal fun LanguageGrid(
    languages: List<Pair<String, Int>>,
    selected: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(languages, key = { it.first }) { (code, count) ->
            val isSelected = code == selected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(code) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                    Text(
                        Labels.lang(code),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$count 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 单条声音；有风格时在行下方展开风格面板。 */
@Composable
internal fun VoicePickerRow(
    voice: Voice,
    isCurrent: Boolean,
    expanded: Boolean,
    currentStyle: String,
    onToggle: () -> Unit,
    onPickStyle: (String?) -> Unit,
) {
    val avatarActive = expanded || isCurrent
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (avatarActive) {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    voice.displayName.take(1).ifBlank { "声" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (avatarActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        voice.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Tag(
                            text = "当前",
                            color = MaterialTheme.colorScheme.tertiary,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                    if (!voice.styles.isNullOrEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Tag(
                            text = "可调风格",
                            color = MaterialTheme.colorScheme.secondary,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                }
                Text(
                    voiceMeta(voice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (isCurrent) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.width(20.dp))
            }
        }
        if (expanded && !voice.styles.isNullOrEmpty()) {
            StylePanel(
                voice = voice,
                selectedStyle = if (isCurrent) currentStyle else "",
                onPick = onPickStyle,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePanel(voice: Voice, selectedStyle: String, onPick: (String?) -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, end = 4.dp, top = 2.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                        ),
                    ),
                )
            }
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Text(
            "先选一个情感风格，点一下立即应用。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StyleChip(
                text = "不加风格",
                selected = selectedStyle.isBlank(),
                onClick = { onPick(null) },
            )
            voice.styles.orEmpty().forEach { style ->
                StyleChip(
                    text = Labels.style(style),
                    selected = style == selectedStyle,
                    onClick = { onPick(style) },
                )
            }
        }
    }
}

@Composable
private fun StyleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Tag(text: String, color: Color, container: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun voiceMeta(voice: Voice): String {
    val lang = Labels.lang(voice.localeLang())
    val region = voice.localeRegion()?.let(Labels::region)
    val gender = Labels.gender(voice.gender)
    val styles = voice.styles?.takeIf { it.isNotEmpty() }
        ?.let { "${it.size} 种风格" }
        ?: "无风格"
    return listOfNotNull(lang, region, gender.takeIf { it.isNotBlank() }, styles)
        .joinToString(" · ")
}
