package com.example.ttsaggregator.ui.screens.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.data.VoiceRefreshResult
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.AppTextField
import com.example.ttsaggregator.ui.util.Labels
import com.example.ttsaggregator.ui.util.voiceLabel
import com.example.ttsaggregator.viewmodel.VoiceViewModel

/**
 * 底部弹层声音选择器（搜索优先）。
 *
 * 默认展示中文分类与最近使用；点声音立即应用，有风格的声音在行内展开风格。
 * 右上角“更新”强制从节点刷新，失败时保留当前内置/缓存列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePickerSheet(
    onDismiss: () -> Unit,
    onApplied: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val vm: VoiceViewModel = appViewModel { VoiceViewModel(it.voiceRepository, it.settings) }
    val voices by vm.voiceList.collectAsState()
    val loading by vm.loading.collectAsState()
    val settings by vm.synthSettings.collectAsState()

    var query by remember { mutableStateOf("") }
    var filterOverride by remember { mutableStateOf<VoiceFilter?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var languageGridOpen by remember { mutableStateOf(false) }
    var clearRecentsConfirm by remember { mutableStateOf(false) }

    val languages = remember(voices) { languageOptions(voices) }
    val availableIds = remember(voices) { voices.map { it.id }.toSet() }
    val recentIds = remember(voices, settings.voice, settings.recentVoices) {
        recentVoiceIds(settings.voice, settings.recentVoices, availableIds)
    }
    val recentVoices = remember(voices, recentIds) {
        recentIds.mapNotNull { id -> voices.find { it.id == id } }
    }
    val recentSet = remember(recentIds) { recentIds.toSet() }
    val filter = filterOverride ?: defaultFilter(recentIds)
    val isFrequent = filter == VoiceFilter.Frequent
    val content = remember(voices, query, filter, recentIds, recentSet) {
        if (query.isBlank()) {
            if (filter == VoiceFilter.Frequent) {
                filterVoices(voices, VoiceFilter.Frequent, recentIds)
            } else {
                filterVoices(voices, filter).filterNot { it.id in recentSet }
            }
        } else {
            searchVoices(voices, query)
        }
    }
    val groups = remember(content, query, isFrequent) {
        if (query.isBlank() && isFrequent) emptyList() else groupVoicesByLanguage(content)
    }
    val showRecentGroup = query.isBlank() && !isFrequent && recentVoices.isNotEmpty()

    val applyVoice: (Voice, String?) -> Unit = { voice, style ->
        vm.updateSettings {
            it.copy(
                voice = voice.id,
                style = style.orEmpty(),
                recentVoices = withRecent(it.recentVoices, voice.id),
            )
        }
        onDismiss()
        onApplied(
            buildString {
                append("已选择：")
                append(voiceLabel(voices, voice))
                if (!style.isNullOrBlank()) {
                    append("·")
                    append(Labels.style(style))
                }
            },
        )
    }

    val toggleVoice: (Voice) -> Unit = { voice ->
        if (voice.styles.isNullOrEmpty()) {
            applyVoice(voice, null)
        } else {
            expandedId = if (expandedId == voice.id) null else voice.id
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "选择声音",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (voices.isEmpty()) {
                            "正在加载声音…"
                        } else {
                            "${voices.size} 个内置声音 · 离线可用"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallIconButton(
                        onClick = {
                            if (loading) return@SmallIconButton
                            vm.refresh { result ->
                                when (result) {
                                    is VoiceRefreshResult.Success ->
                                        onToast("已更新 ${result.count} 个声音")
                                    is VoiceRefreshResult.Failure ->
                                        onToast("更新失败：${result.reason}，继续使用内置数据")
                                    VoiceRefreshResult.Busy -> onToast("正在更新，请稍候")
                                }
                            }
                        },
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "更新声音",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    SmallIconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AppTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                placeholder = "搜索声音、语言或地区",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { query = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "清除搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
            )

            if (languageGridOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "全部语言",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "返回",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { languageGridOpen = false }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                LanguageGrid(
                    languages = languages,
                    selected = (filter as? VoiceFilter.Language)?.code,
                    onPick = { code ->
                        filterOverride = VoiceFilter.Language(code)
                        query = ""
                        languageGridOpen = false
                        expandedId = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            } else {
                VoiceFilterChips(
                    languages = languages,
                    filter = filter,
                    onFilter = {
                        filterOverride = it
                        query = ""
                        expandedId = null
                    },
                    onMoreLanguages = { languageGridOpen = true },
                )

                when {
                    loading && voices.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    voices.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "暂无声音数据，点右上角更新",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    content.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "没有匹配的声音",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (query.isNotBlank()) "清除搜索" else "回到中文",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (query.isNotBlank()) {
                                                query = ""
                                            } else {
                                                filterOverride = VoiceFilter.Chinese
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 10.dp),
                        ) {
                            if (query.isNotBlank()) {
                                item(key = "search-caption") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 8.dp),
                                    ) {
                                        Text(
                                            "${content.size} 个声音 · 中文优先",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "点击即应用",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (query.isBlank() && isFrequent) {
                                item(key = "recent-header") {
                                    SectionHeader(
                                        title = "最近使用",
                                        count = content.size,
                                        actionLabel = "删除记录",
                                        onAction = { clearRecentsConfirm = true },
                                    )
                                }
                                items(content, key = { "frequent-${it.id}" }) { voice ->
                                    VoicePickerRow(
                                        voice = voice,
                                        label = voiceLabel(voices, voice),
                                        isCurrent = voice.id == settings.voice,
                                        expanded = expandedId == voice.id,
                                        currentStyle = settings.style,
                                        onToggle = { toggleVoice(voice) },
                                        onPickStyle = { style -> applyVoice(voice, style) },
                                    )
                                }
                            }
                            if (showRecentGroup) {
                                item(key = "recent-header") {
                                    SectionHeader(
                                        title = "最近使用",
                                        count = recentVoices.size,
                                        actionLabel = "删除记录",
                                        onAction = { clearRecentsConfirm = true },
                                    )
                                }
                                items(recentVoices, key = { "recent-${it.id}" }) { voice ->
                                    VoicePickerRow(
                                        voice = voice,
                                        label = voiceLabel(voices, voice),
                                        isCurrent = voice.id == settings.voice,
                                        expanded = expandedId == voice.id,
                                        currentStyle = settings.style,
                                        onToggle = { toggleVoice(voice) },
                                        onPickStyle = { style -> applyVoice(voice, style) },
                                    )
                                }
                            }
                            groups.forEach { (lang, list) ->
                                item(key = "group-$lang") {
                                    SectionHeader("${Labels.lang(lang)}声音", list.size)
                                }
                                items(list, key = { "voice-${it.id}" }) { voice ->
                                    VoicePickerRow(
                                        voice = voice,
                                        label = voiceLabel(voices, voice),
                                        isCurrent = voice.id == settings.voice,
                                        expanded = expandedId == voice.id,
                                        currentStyle = settings.style,
                                        onToggle = { toggleVoice(voice) },
                                        onPickStyle = { style -> applyVoice(voice, style) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "默认中文置顶；知道名字就直接搜",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            vm.updateSettings { it.copy(voice = "", style = "") }
                            onDismiss()
                            onApplied("已恢复默认音色")
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        "恢复默认",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (clearRecentsConfirm) {
        AlertDialog(
            onDismissRequest = { clearRecentsConfirm = false },
            title = {
                Text(
                    "删除最近使用记录？",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    "删除后将不再显示最近使用的音色，此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.updateSettings { it.copy(recentVoices = emptyList()) }
                        if (filter == VoiceFilter.Frequent) {
                            filterOverride = VoiceFilter.Chinese
                        }
                        expandedId = null
                        clearRecentsConfirm = false
                        onToast("已删除最近使用记录")
                    },
                ) {
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearRecentsConfirm = false }) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun SmallIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 14.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.weight(1f))
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
