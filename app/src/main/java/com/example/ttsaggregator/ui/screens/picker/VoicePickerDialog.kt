package com.example.ttsaggregator.ui.screens.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.ui.util.Labels
import com.example.ttsaggregator.viewmodel.VoiceViewModel

/**
 * 四级声音选择器（语言 ▸ 地区 ▸ 声音 ▸ 风格）。
 * 选择状态机见 [VoicePick]。
 */
@Composable
fun VoicePickerDialog(
    vm: VoiceViewModel,
    onDismiss: () -> Unit,
    onApplied: (String) -> Unit,
) {
    val voices by vm.voiceList.collectAsState()
    val loading by vm.loading.collectAsState()
    val settings by vm.synthSettings.collectAsState()

    // 从当前设置预选（解析 short_name 前两级）
    val initial = remember(settings.voice, voices) {
        val cur = voices.find { it.id == settings.voice }
        VoicePick(
            level = if (cur != null) 3 else 0,
            lang = cur?.id?.split('-')?.getOrNull(0),
            region = cur?.id?.split('-')?.getOrNull(1),
            voice = cur,
            style = null,
        )
    }
    var pick by remember(settings.voice, voices) { mutableStateOf(initial) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                // 顶栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "选择声音",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }

                // 步骤条
                val labels = listOf("1.语言", "2.地区", "3.声音", "4.风格")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // 面包屑
                val crumb = pick.crumb()
                Text(
                    crumb.ifBlank { "从语言开始选择" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )

                // 内容
                when {
                    loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(40.dp),
                        )
                    }
                    voices.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "音色列表为空",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { vm.loadVoices(force = true) }) { Text("重新拉取") }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            when (pick.level) {
                                0 -> {
                                    items(voices.map { it.id.split('-')[0] }.distinct()) { lang ->
                                        PickerOption(
                                            title = Labels.lang(lang),
                                            desc = "$lang · ${voices.count { it.id.startsWith("$lang-") }} 个声音",
                                            selected = pick.lang == lang,
                                            onClick = { pick = pick.pickLang(lang) },
                                        )
                                    }
                                }
                                1 -> {
                                    val regions = voices.filter { it.id.startsWith("${pick.lang}-") }
                                        .map { it.id.split('-')[1] }.distinct()
                                    items(regions) { r ->
                                        PickerOption(
                                            title = Labels.region(r),
                                            desc = "${pick.lang}-$r · ${voices.count { it.id.startsWith("${pick.lang}-$r-") }} 个声音",
                                            selected = pick.region == r,
                                            onClick = { pick = pick.pickRegion(r) },
                                        )
                                    }
                                }
                                2 -> {
                                    val vs = voices.filter { it.id.startsWith("${pick.lang}-${pick.region}-") }
                                    items(vs, key = { it.id }) { v ->
                                        PickerOption(
                                            title = v.displayName,
                                            desc = "${v.id} · ${Labels.gender(v.gender)}" +
                                                (v.styles?.takeIf { it.isNotEmpty() }?.let { " · ${it.size} 种风格" } ?: " · 无风格"),
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
                                                "⚠ 该声音不支持风格\n（应用时将不带 style 参数）",
                                                modifier = Modifier.padding(vertical = 32.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    } else {
                                        item {
                                            PickerOption(
                                                title = "不加风格",
                                                desc = "不带 style 参数",
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

                // 底部：说明 + 应用
                Text(
                    "风格影响情感表达，仅部分声音支持（来自其 style_list）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        vm.updateSettings { it.copy(voice = "") }
                        onDismiss()
                        onApplied("已恢复默认音色")
                    }) { Text("恢复默认", style = MaterialTheme.typography.labelLarge) }
                    Button(onClick = {
                        val v = pick.voice ?: return@Button
                        vm.updateSettings { it.copy(voice = v.id) }
                        onDismiss()
                        onApplied(pick.summary(v))
                    }, enabled = pick.voice != null) {
                        Text("应用", style = MaterialTheme.typography.labelLarge)
                    }
                }
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
    fun pickVoice(v: Voice) = VoicePick(3, lang, region, v, null)
    fun pickStyle(s: String?) = VoicePick(3, lang, region, voice, s)
    fun goto(i: Int): VoicePick = when (i) {
        0 -> VoicePick(0, null, null, null, null)
        1 -> if (lang != null) VoicePick(1, lang, null, null, null) else this
        2 -> if (region != null) VoicePick(2, lang, region, null, null) else this
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

@Composable
private fun PickerOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = if (selected) null
        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
