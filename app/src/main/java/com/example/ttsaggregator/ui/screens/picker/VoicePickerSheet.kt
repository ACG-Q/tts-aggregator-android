package com.example.ttsaggregator.ui.screens.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.data.VoiceRefreshResult
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.util.Labels
import com.example.ttsaggregator.viewmodel.VoiceViewModel

/**
 * 底部弹层声音选择器（语言 ▸ 地区 ▸ 声音 ▸ 风格）。
 *
 * 内置数据保证离线可用；右上角“更新”强制从节点刷新，
 * 网络失败时保留当前内置/缓存列表。选择状态机见 [VoicePick]。
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

    val initial = remember(settings.voice, voices) {
        val cur = voices.find { it.id == settings.voice }
        VoicePick(
            level = if (cur != null) 2 else 0,
            lang = cur?.localeLang(),
            region = cur?.localeRegion(),
            voice = cur,
            style = settings.style.takeIf { it.isNotBlank() },
        )
    }
    var pick by remember(settings.voice, voices) { mutableStateOf(initial) }
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
                        "语言 ▸ 地区 ▸ 声音 ▸ 风格",
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            ) {
                Text(
                    "当前 ${voices.size} 个声音，离线可用；点“更新”从节点刷新，失败时继续使用内置数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            val labels = listOf("语言", "地区", "声音", "风格")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                labels.forEachIndexed { i, label ->
                    val cur = pick.level == i
                    val done = pick.doneAt(i)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { pick = pick.goto(i) },
                        shape = MaterialTheme.shapes.extraSmall,
                        color = when {
                            cur -> MaterialTheme.colorScheme.primary
                            done -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = when {
                            cur -> MaterialTheme.colorScheme.onPrimary
                            done -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Text(
                            "${i + 1} $label",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 7.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            Text(
                pick.crumb().ifBlank { "从语言开始选择" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                            "暂无声音数据，请检查节点后重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    // 进入声音层级时定位到当前已选声音，避免选中项藏在列表深处
                    val selectedIndex = if (pick.level == 2) {
                        voices.filter {
                            it.localeLang() == pick.lang && it.localeRegion() == pick.region
                        }.indexOfFirst { it.id == pick.voice?.id }
                    } else {
                        -1
                    }
                    val listState = remember(pick.level, pick.lang, pick.region) {
                        LazyListState(selectedIndex.coerceAtLeast(0))
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        when (pick.level) {
                            0 -> {
                                items(voices.map { it.localeLang() }.distinct()) { lang ->
                                    PickerOption(
                                        title = Labels.lang(lang),
                                        desc = "$lang · ${voices.count { it.localeLang() == lang }} 个声音",
                                        selected = pick.lang == lang,
                                        onClick = { pick = pick.pickLang(lang) },
                                    )
                                }
                            }
                            1 -> {
                                val regions = voices.filter { it.localeLang() == pick.lang }
                                    .mapNotNull { it.localeRegion() }.distinct()
                                items(regions) { r ->
                                    PickerOption(
                                        title = Labels.region(r),
                                        desc = "$r · ${voices.count { it.localeLang() == pick.lang && it.localeRegion() == r }} 个声音",
                                        selected = pick.region == r,
                                        onClick = { pick = pick.pickRegion(r) },
                                    )
                                }
                            }
                            2 -> {
                                val vs = voices.filter {
                                    it.localeLang() == pick.lang && it.localeRegion() == pick.region
                                }
                                items(vs, key = { it.id }) { v ->
                                    VoiceRow(
                                        voice = v,
                                        selected = pick.voice?.id == v.id,
                                        onClick = { pick = pick.pickVoice(v) },
                                    )
                                }
                            }
                            else -> {
                                val styles = pick.voice?.styles.orEmpty()
                                if (styles.isEmpty()) {
                                    item {
                                        Text(
                                            "该声音不支持情感风格，应用时不会带 style 参数。",
                                            modifier = Modifier.padding(vertical = 28.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    item {
                                        PickerOption(
                                            title = "不加风格",
                                            desc = "保持声音原本的语气",
                                            selected = pick.style == null,
                                            onClick = { pick = pick.pickStyle(null) },
                                        )
                                    }
                                    items(styles) { s ->
                                        PickerOption(
                                            title = Labels.style(s),
                                            desc = s,
                                            selected = pick.style == s,
                                            onClick = { pick = pick.pickStyle(s) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(voice = "", style = "") }
                    onDismiss()
                    onApplied("已恢复默认音色")
                }) { Text("恢复默认") }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        val v = pick.voice ?: return@Button
                        vm.updateSettings { it.copy(voice = v.id, style = pick.style.orEmpty()) }
                        onDismiss()
                        onApplied("已选择：${pick.summary(v)}")
                    },
                    enabled = pick.voice != null,
                ) { Text("应用") }
            }
        }
    }
}

/** 四级选择状态机 */
data class VoicePick(
    val level: Int,
    val lang: String?,
    val region: String?,
    val voice: Voice?,
    val style: String?,
) {
    fun doneAt(i: Int) = listOfNotNull(lang, region, voice, style).size > i
    fun pickLang(l: String) = VoicePick(1, l, null, null, null)
    fun pickRegion(r: String) = VoicePick(2, lang, r, null, null)

    /** 有情感风格的声音继续走风格步骤，没有则留在声音列表直接应用 */
    fun pickVoice(v: Voice) = VoicePick(
        level = if (v.styles.isNullOrEmpty()) 2 else 3,
        lang = lang,
        region = region,
        voice = v,
        style = null,
    )
    fun pickStyle(s: String?) = VoicePick(3, lang, region, voice, s)
    fun goto(i: Int): VoicePick = when (i) {
        0 -> VoicePick(0, null, null, null, null)
        1 -> if (lang != null) VoicePick(1, lang, null, null, null) else this
        2 -> if (region != null) VoicePick(2, lang, region, voice, null) else this
        else -> if (voice?.styles?.isNotEmpty() == true) VoicePick(3, lang, region, voice, style) else this
    }

    /** 面包屑：语言 ▸ 地区 ▸ 声音 ▸ 风格 */
    fun crumb(): String = listOfNotNull(
        lang?.let { Labels.lang(it) },
        region?.let { Labels.region(it) },
        voice?.displayName,
        style?.let { Labels.style(it) },
    ).joinToString(" ▸ ")

    /** 应用后的摘要文案 */
    fun summary(v: Voice): String = listOfNotNull(
        lang?.let { Labels.lang(it) },
        region?.let { Labels.region(it) },
        v.displayName,
        style?.let { Labels.style(it) },
    ).joinToString(" · ")
}

/**
 * locale 解析：兼容 `zh-CN`、`iu-Cans-CA`（含四字母脚本子标签）与 `zh-CN-guangxi`（方言）。
 * 语言取首段，地区跳过脚本子标签后取实际地区。
 */
internal fun Voice.localeLang(): String = localeSegments(this).first

internal fun Voice.localeRegion(): String? = localeSegments(this).second

private fun localeSegments(voice: Voice): Pair<String, String?> {
    val parts = voice.locale.ifBlank { voice.id }.split('-')
    val region = when {
        parts.size >= 3 && parts[1].length == 4 -> parts[2]
        parts.size >= 2 -> parts[1]
        else -> null
    }
    return parts.firstOrNull().orEmpty() to region
}

@Composable
private fun VoiceRow(voice: Voice, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant,
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
                    voice.nameZh.take(1).ifBlank { "声" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        voice.nameZh,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Labels.gender(voice.gender),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    voice.introZh.ifBlank { "${voice.id} · ${Labels.gender(voice.gender)}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PickerOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
