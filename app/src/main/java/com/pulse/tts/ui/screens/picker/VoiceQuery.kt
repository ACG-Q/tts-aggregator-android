package com.pulse.tts.ui.screens.picker

import com.pulse.tts.data.Voice
import com.pulse.tts.ui.util.Labels

/** 选择器筛选：常用 / 中文 / 全部 / 指定语言。 */
internal sealed interface VoiceFilter {
    data object Frequent : VoiceFilter
    data object Chinese : VoiceFilter
    data object All : VoiceFilter
    data class Language(val code: String) : VoiceFilter
}

/** 有最近记录时默认「常用」，否则默认「中文」。 */
internal fun defaultFilter(recentIds: List<String>): VoiceFilter =
    if (recentIds.isEmpty()) VoiceFilter.Chinese else VoiceFilter.Frequent

internal fun VoiceFilter.matches(voice: Voice): Boolean = when (this) {
    VoiceFilter.Frequent -> false
    VoiceFilter.Chinese -> voice.localeLang() == "zh"
    VoiceFilter.All -> true
    is VoiceFilter.Language -> voice.localeLang() == code
}

internal fun filterVoices(
    voices: List<Voice>,
    filter: VoiceFilter,
    recentIds: List<String> = emptyList(),
): List<Voice> = when (filter) {
    VoiceFilter.Frequent -> {
        val byId = voices.associateBy { it.id }
        recentIds.mapNotNull(byId::get)
    }
    else -> voices.filter(filter::matches).sortedWith(voiceOrder)
}

/**
 * 顶部 chips 的固定顺序：常用 → 全部 → 中文 → 英语 → 其余语言（按声音数降序）。
 */
internal data class ChipEntry(val label: String, val filter: VoiceFilter)

internal fun chipEntries(languages: List<Pair<String, Int>>): List<ChipEntry> {
    val available = languages.mapTo(mutableSetOf()) { it.first }
    val rest = languages
        .filter { it.first != "zh" && it.first != "en" }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .map { it.first }
    return buildList {
        add(ChipEntry("常用", VoiceFilter.Frequent))
        add(ChipEntry("全部", VoiceFilter.All))
        if ("zh" in available) add(ChipEntry("中文", VoiceFilter.Chinese))
        if ("en" in available) add(ChipEntry("英语", VoiceFilter.Language("en")))
        rest.forEach { add(ChipEntry(Labels.lang(it), VoiceFilter.Language(it))) }
    }
}

/** chips 动态排布结果：显示前 [visibleCount] 个，[overflow] 为 true 时追加「更多」。 */
internal data class ChipPlan(val visibleCount: Int, val overflow: Boolean)

/**
 * 按可用宽度计算 chips：全部放下则不溢出；否则保留能放下的前缀并预留「更多」。
 * 宽度、间距与可用空间使用同一单位（像素）。
 */
internal fun planChips(
    widths: List<Float>,
    available: Float,
    spacing: Float,
    moreWidth: Float,
): ChipPlan {
    if (widths.isEmpty()) return ChipPlan(0, false)
    val total = widths.sum() + spacing * (widths.size - 1)
    if (total <= available) return ChipPlan(widths.size, false)

    var used = 0f
    var count = 0
    while (count < widths.size) {
        val next = if (count == 0) widths[0] else used + spacing + widths[count]
        if (next + spacing + moreWidth <= available) {
            used = next
            count++
        } else {
            break
        }
    }
    return ChipPlan(count, true)
}

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

/**
 * 最近使用：仅在存在历史记录时并入当前声音，去重、过滤失效 ID、最多 6 条。
 * 没有历史记录时返回空列表，整个最近分组因此隐藏。
 */
internal fun recentVoiceIds(
    currentId: String,
    stored: List<String>,
    available: Set<String>,
): List<String> {
    if (stored.isEmpty()) return emptyList()
    return (listOf(currentId) + stored)
        .filter { it.isNotBlank() }
        .distinct()
        .filter { it in available }
        .take(6)
}

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
