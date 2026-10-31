package com.example.ttsaggregator.ui.screens.picker

import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.ui.util.Labels

/** 选择器筛选：中文 / 全部 / 指定语言。 */
internal sealed interface VoiceFilter {
    data object Chinese : VoiceFilter
    data object All : VoiceFilter
    data class Language(val code: String) : VoiceFilter
}

internal fun VoiceFilter.matches(voice: Voice): Boolean = when (this) {
    VoiceFilter.Chinese -> voice.localeLang() == "zh"
    VoiceFilter.All -> true
    is VoiceFilter.Language -> voice.localeLang() == code
}

internal fun filterVoices(voices: List<Voice>, filter: VoiceFilter): List<Voice> =
    voices.filter(filter::matches).sortedWith(voiceOrder)

/** 全部语言的展示顺序：中文第一，其余按声音数降序。 */
internal fun languageOptions(voices: List<Voice>): List<Pair<String, Int>> =
    voices.groupingBy { it.localeLang() }.eachCount().entries
        .sortedWith(compareBy({ if (it.key == "zh") 0 else 1 }, { -it.value }, { it.key }))
        .map { it.key to it.value }

/** 搜索始终覆盖全部声音；中文结果排在最前。 */
internal fun searchVoices(voices: List<Voice>, query: String): List<Voice> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return voices
    return voices.filter { it.searchText().contains(q) }
        .sortedWith(
            compareBy(
                { if (it.localeLang() == "zh") 0 else 1 },
                { it.locale },
                { it.displayName },
            ),
        )
}

/** 按语言分组；中文组置顶，其余按组内声音数降序。 */
internal fun groupVoicesByLanguage(voices: List<Voice>): List<Pair<String, List<Voice>>> =
    voices.groupBy { it.localeLang() }.entries
        .sortedWith(compareBy({ if (it.key == "zh") 0 else 1 }, { -it.value.size }, { it.key }))
        .map { it.key to it.value.sortedWith(voiceOrder) }

/** 最近使用：当前声音 + 历史记录，去重、过滤失效 ID、最多 6 条。 */
internal fun recentVoiceIds(
    currentId: String,
    stored: List<String>,
    available: Set<String>,
): List<String> = buildList {
    if (currentId.isNotBlank()) add(currentId)
    addAll(stored)
}.distinct().filter { it in available }.take(6)

/** 应用新声音后更新最近记录：最新在前、去重、最多 6 条。 */
internal fun withRecent(stored: List<String>, appliedId: String, max: Int = 6): List<String> =
    (listOf(appliedId) + stored).distinct().take(max)

private val voiceOrder = compareBy<Voice>({ it.locale }, { it.displayName })

private fun Voice.searchText(): String {
    val lang = localeLang()
    val region = localeRegion()
    return buildString {
        append(nameZh)
        append(' ')
        append(name)
        append(' ')
        append(id)
        append(' ')
        append(baseName)
        append(' ')
        append(Labels.lang(lang))
        append(' ')
        if (region != null) append(Labels.region(region))
    }.lowercase()
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
