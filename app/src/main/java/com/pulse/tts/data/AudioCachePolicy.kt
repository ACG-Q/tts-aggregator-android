package com.pulse.tts.data

import java.security.MessageDigest

/**
 * 语音缓存的键生成与失效策略。
 *
 * 缓存键只由「文本 + 音色 + 语速 + 音调」决定，不含节点与风格：
 * 同一段话换节点也会命中，避免轮询/切换节点时重复请求同一站点。
 * 全部是纯函数，便于在 JVM 单测里验证。
 */

/** 缓存格式版本：键规则变化时递增，旧文件自然失效 */
internal const val AUDIO_CACHE_VERSION = 1

/** 单个缓存目录的容量上限，超出后按最久未使用淘汰 */
internal const val AUDIO_CACHE_LIMIT_BYTES = 200L * 1024 * 1024

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val HEX = "0123456789abcdef"

/** 生成 SHA-256 十六进制摘要，直接用作文件名 */
internal fun audioCacheKey(text: String, prefs: SynthPrefs): String {
    val raw = buildString {
        append(AUDIO_CACHE_VERSION)
        append('\n')
        append(text)
        append('\n')
        append(prefs.voice)
        append('\n')
        append(prefs.rate)
        append('\n')
        append(prefs.pitch)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }
}

/** 缓存目录中的一个音频文件 */
internal data class AudioCacheEntry(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * 是否过期。
 *
 * [ttlDays] <= 0 表示缓存关闭，调用方本就不该读取；这里返回 true 作为兜底。
 * 读取时会把文件的最后修改时间更新为当前时间，因此频繁复用的音频会持续续期。
 */
internal fun isAudioCacheExpired(lastModified: Long, now: Long, ttlDays: Int): Boolean {
    if (ttlDays <= 0) return true
    if (lastModified <= 0) return true
    return now - lastModified > ttlDays * DAY_MS
}

/** 从最久未使用的文件开始淘汰，直到总量不超过 [limitBytes] */
internal fun selectAudioCacheEvictions(
    entries: List<AudioCacheEntry>,
    limitBytes: Long,
): List<AudioCacheEntry> {
    var total = entries.sumOf { it.sizeBytes }
    if (total <= limitBytes) return emptyList()
    val victims = mutableListOf<AudioCacheEntry>()
    for (entry in entries.sortedWith(compareBy({ it.lastModified }, { it.name }))) {
        if (total <= limitBytes) break
        victims += entry
        total -= entry.sizeBytes
    }
    return victims
}
