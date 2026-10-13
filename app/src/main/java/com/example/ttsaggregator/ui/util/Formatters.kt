package com.example.ttsaggregator.ui.util

import com.example.ttsaggregator.data.Voice
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
 * 音色 ID → 中文显示名。
 * 优先从已加载的音色列表匹配（displayName 含本地化+系列中文化）；
 * 列表未加载时降级：拆 short_name 用 Labels 展示「语言 · 地区 · 名字」。
 */
fun voiceDisplayName(voices: List<Voice>, id: String): String {
    if (id.isBlank()) return "默认音色"
    voices.find { it.id == id }?.let { return it.displayName }
    // 降级：zh-CN-XiaoxiaoNeural → 中文 · 简体 · Xiaoxiao
    val parts = id.split('-')
    return if (parts.size >= 3) {
        val name = parts.drop(2).joinToString("-").removeSuffix("Neural").substringBefore(':')
        "${Labels.lang(parts[0])} · ${Labels.region(parts[1])} · $name"
    } else {
        id
    }
}
