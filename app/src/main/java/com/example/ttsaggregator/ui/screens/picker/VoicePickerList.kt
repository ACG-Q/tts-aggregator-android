package com.example.ttsaggregator.ui.screens.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.ui.util.Labels

/** 语言快捷筛选：中文固定第一，全部固定第二，其余取声音最多的三种语言。 */
@Composable
internal fun VoiceFilterChips(
    languages: List<Pair<String, Int>>,
    filter: VoiceFilter,
    onFilter: (VoiceFilter) -> Unit,
    onMoreLanguages: () -> Unit,
) {
    val quick = languages.filter { it.first != "zh" }.take(3)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "chip-zh") {
            FilterChip(
                selected = filter == VoiceFilter.Chinese,
                onClick = { onFilter(VoiceFilter.Chinese) },
                label = { Text("中文") },
            )
        }
        item(key = "chip-all") {
            FilterChip(
                selected = filter == VoiceFilter.All,
                onClick = { onFilter(VoiceFilter.All) },
                label = { Text("全部") },
            )
        }
        items(quick, key = { it.first }) { (code, _) ->
            FilterChip(
                selected = filter is VoiceFilter.Language && filter.code == code,
                onClick = { onFilter(VoiceFilter.Language(code)) },
                label = { Text(Labels.lang(code)) },
            )
        }
        item(key = "chip-more") {
            FilterChip(
                selected = false,
                onClick = onMoreLanguages,
                label = { Text("更多语言…") },
            )
        }
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
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                    Text(
                        Labels.lang(code),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
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
    onToggle: () -> Unit,
    onPickStyle: (String?) -> Unit,
) {
    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            shape = MaterialTheme.shapes.medium,
            color = if (expanded || isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                1.dp,
                if (expanded || isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        voice.displayName.take(1).ifBlank { "声" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            voice.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isCurrent) {
                            Spacer(Modifier.width(6.dp))
                            Tag(
                                text = "当前",
                                color = MaterialTheme.colorScheme.primary,
                                container = MaterialTheme.colorScheme.primaryContainer,
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
                    )
                    if (voice.introZh.isNotBlank()) {
                        Text(
                            voice.introZh,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isCurrent) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (expanded && !voice.styles.isNullOrEmpty()) {
            StylePanel(voice = voice, onPick = onPickStyle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePanel(voice: Voice, onPick: (String?) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
    ) {
        Text(
            "选择情感风格，点一下立即应用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StyleChip(text = "不加风格") { onPick(null) }
            voice.styles.orEmpty().forEach { style ->
                StyleChip(text = Labels.style(style)) { onPick(style) }
            }
        }
    }
}

@Composable
private fun StyleChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
