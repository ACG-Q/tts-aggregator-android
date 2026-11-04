package com.pulse.tts.ui.util

import com.pulse.tts.data.Voice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 纯函数工具：时间 / 耗时 / 音色显示名。
 * 无 Compose 依赖，方便复用与测试。
 */

private val timeFmtCache = HashMap<String, SimpleDateFormat>()

/** 线程内复用 SimpleDateFormat（格式串 → formatter） */
private fun fmt(pattern: String): SimpleDateFormat =
    timeFmtCache.getOrPut(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }

fun formatClock(time: Long, pattern: String = "HH:mm:ss"): String = fmt(pattern).format(Date(time))

/** 耗时：<1s 显示 ms，否则显示 1 位小数秒 */
fun formatLatency(ms: Long): String =
    if (ms < 1000) "${ms}ms" else "%.1fs".format(ms / 1000.0)

/** 节点 URL → 短显示（去掉协议前缀） */
fun shortUrl(url: String, max: Int = 24): String =
    url.removePrefix("https://").removePrefix("http://").take(max)

/**
 * 同名声音的变体标记，例如 HD Flash / 多语言 / 方言。
 * 依据 short_name 后缀推导，不依赖额外的数据字段。
 */
private fun variantTag(id: String): String = when {
    id.contains("Multilingual", ignoreCase = true) -> "多语言"
    id.contains("Dialects", ignoreCase = true) -> "方言"
    id.contains("DragonHDFlashLatest", ignoreCase = true) -> "HD Flash"
    id.contains("DragonHDLatest", ignoreCase = true) ||
        id.contains(":DragonHD", ignoreCase = true) -> "HD"
    id.contains("DragonLatest", ignoreCase = true) ||
        id.contains(":Dragon", ignoreCase = true) -> "Dragon"
    else -> "标准"
}

/** 语言，用于同名且变体标记也相同的跨语言声音。 */
private fun localeLang(voice: Voice): String =
    Labels.lang(voice.locale.substringBefore('-'))

/** 语言 · 地区，语言仍冲突时使用。 */
private fun localeTag(voice: Voice): String {
    val region = voice.locale.substringAfter('-', "")
    return if (region.isBlank()) localeLang(voice) else
        "${localeLang(voice)} · ${Labels.region(region)}"
}

/**
 * 声音的完整名称。
 * 无重名时直接使用 displayName；同名时追加括号变体标记，
 * 变体标记仍冲突（跨语言同名）时改用语言，语言也相同时再补地区。
 */
fun voiceLabel(voices: List<Voice>, voice: Voice): String {
    val base = voice.displayName.ifBlank { voice.id }
    if (voices.count { it.displayName == voice.displayName } <= 1) return base

    val tag = variantTag(voice.id)
    val sameTag = voices.filter {
        it.displayName == voice.displayName && variantTag(it.id) == tag
    }
    if (sameTag.size <= 1) return "$base（$tag）"

    val lang = localeLang(voice)
    val sameLang = sameTag.filter { localeLang(it) == lang }
    if (sameLang.size <= 1) return "$base（$lang）"

    val locale = localeTag(voice)
    return if (sameLang.count { localeTag(it) == locale } <= 1) {
        "$base（$locale）"
    } else {
        "$base（$locale · ${voice.id}）"
    }
}

/**
 * 音色 ID → 显示名，可附加当前风格。
 * 优先从已加载的音色列表匹配（含变体去重与风格）；
 * 列表未加载时降级：拆 short_name 用 Labels 展示「语言 · 地区 · 名字」。
 */
fun voiceDisplayName(voices: List<Voice>, id: String, style: String = ""): String {
    if (id.isBlank()) return "默认音色"
    val voice = voices.find { it.id == id }
    if (voice != null) {
        val name = voiceLabel(voices, voice)
        val styleLabel = if (
            style.isNotBlank() && voice.styles.orEmpty().contains(style)
        ) {
            Labels.style(style)
        } else {
            ""
        }
        return if (styleLabel.isBlank()) name else "$name·$styleLabel"
    }
    // 降级：zh-CN-XiaoxiaoNeural → 中文 · 简体 · Xiaoxiao
    val parts = id.split('-')
    val fallback = if (parts.size >= 3) {
        val name = parts.drop(2).joinToString("-").removeSuffix("Neural").substringBefore(':')
        "${Labels.lang(parts[0])} · ${Labels.region(parts[1])} · $name"
    } else {
        id
    }
    return if (style.isBlank()) fallback else "$fallback·${Labels.style(style)}"
}
