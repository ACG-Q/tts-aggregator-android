package com.example.ttsaggregator.ui.screens.picker

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.data.VoiceRefreshResult
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.util.Labels
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
    var filter by remember { mutableStateOf<VoiceFilter>(VoiceFilter.Chinese) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var languageGridOpen by remember { mutableStateOf(false) }

    val languages = remember(voices) { languageOptions(voices) }
    val availableIds = remember(voices) { voices.map { it.id }.toSet() }
    val recentIds = remember(voices, settings.voice, settings.recentVoices) {
        recentVoiceIds(settings.voice, settings.recentVoices, availableIds)
    }
    val recentVoices = remember(voices, recentIds) {
        recentIds.mapNotNull { id -> voices.find { it.id == id } }
    }
    val recentSet = remember(recentIds) { recentIds.toSet() }
    val content = remember(voices, query, filter, recentSet) {
        if (query.isBlank()) {
            filterVoices(voices, filter).filterNot { it.id in recentSet }
        } else {
            searchVoices(voices, query)
        }
    }
    val groups = remember(content) { groupVoicesByLanguage(content) }

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
                append(voice.displayName)
                if (!style.isNullOrBlank()) {
                    append(" · ")
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
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "选择声音",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (voices.isEmpty()) {
                            "正在加载声音…"
                        } else {
                            "${voices.size} 个内置声音 · 离线可用"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        if (loading) return@TextButton
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
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(if (loading) "更新中" else "更新")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("搜索声音、语言或地区") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            if (languageGridOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "全部语言",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { languageGridOpen = false }) {
                        Text("返回")
                    }
                }
                LanguageGrid(
                    languages = languages,
                    selected = (filter as? VoiceFilter.Language)?.code,
                    onPick = { code ->
                        filter = VoiceFilter.Language(code)
                        query = ""
                        languageGridOpen = false
                        expandedId = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
            } else {
                VoiceFilterChips(
                    languages = languages,
                    filter = filter,
                    onFilter = {
                        filter = it
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
                    groups.isEmpty() -> {
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
                                TextButton(
                                    onClick = {
                                        if (query.isNotBlank()) {
                                            query = ""
                                        } else {
                                            filter = VoiceFilter.Chinese
                                        }
                                    },
                                ) {
                                    Text(if (query.isNotBlank()) "清除搜索" else "回到中文")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            if (query.isBlank() && recentVoices.isNotEmpty()) {
                                item(key = "recent-header") {
                                    SectionHeader("最近使用", recentVoices.size)
                                }
                                items(recentVoices, key = { "recent-${it.id}" }) { voice ->
                                    VoicePickerRow(
                                        voice = voice,
                                        isCurrent = voice.id == settings.voice,
                                        expanded = expandedId == voice.id,
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
                                        isCurrent = voice.id == settings.voice,
                                        expanded = expandedId == voice.id,
                                        onToggle = { toggleVoice(voice) },
                                        onPickStyle = { style -> applyVoice(voice, style) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "点击声音即可应用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        vm.updateSettings { it.copy(voice = "", style = "") }
                        onDismiss()
                        onApplied("已恢复默认音色")
                    },
                ) {
                    Text("恢复默认")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
