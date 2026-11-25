package com.pulse.tts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCachePolicyTest {

    private val prefs = SynthPrefs(voice = "zh-CN-XiaoxiaoNeural", rate = 10, pitch = -5)

    @Test
    fun `缓存键对相同输入稳定`() {
        assertEquals(audioCacheKey("今天天气不错", prefs), audioCacheKey("今天天气不错", prefs))
    }

    @Test
    fun `缓存键随文本音色语速音调变化`() {
        val base = audioCacheKey("今天天气不错", prefs)
        assertNotEquals(base, audioCacheKey("今天天气很好", prefs))
        assertNotEquals(base, audioCacheKey("今天天气不错", prefs.copy(voice = "zh-CN-YunxiNeural")))
        assertNotEquals(base, audioCacheKey("今天天气不错", prefs.copy(rate = 20)))
        assertNotEquals(base, audioCacheKey("今天天气不错", prefs.copy(pitch = 5)))
    }

    @Test
    fun `风格与节点不参与缓存键`() {
        val base = audioCacheKey("今天天气不错", prefs)
        assertEquals(base, audioCacheKey("今天天气不错", prefs.copy(style = "cheerful")))
    }

    @Test
    fun `缓存键是 64 位十六进制`() {
        val key = audioCacheKey("文本", prefs)
        assertEquals(64, key.length)
        assertTrue(key.all { it in "0123456789abcdef" })
    }

    @Test
    fun `超过保留天数才过期`() {
        val now = 1_700_000_000_000L
        val day = 24L * 60 * 60 * 1000
        assertFalse(isAudioCacheExpired(now - day, now, ttlDays = 7))
        assertFalse(isAudioCacheExpired(now - 7 * day, now, ttlDays = 7))
        assertTrue(isAudioCacheExpired(now - 8 * day, now, ttlDays = 7))
        assertTrue(isAudioCacheExpired(now - day, now, ttlDays = 0))
    }

    @Test
    fun `时钟回拨不误删缓存`() {
        val now = 1_700_000_000_000L
        assertFalse(isAudioCacheExpired(now + 60_000, now, ttlDays = 7))
    }

    @Test
    fun `未超出容量时不淘汰`() {
        val entries = listOf(
            AudioCacheEntry("a.mp3", 100, 1),
            AudioCacheEntry("b.mp3", 100, 2),
        )
        assertTrue(selectAudioCacheEvictions(entries, limitBytes = 300).isEmpty())
    }

    @Test
    fun `超出容量时按最久未使用淘汰`() {
        val entries = listOf(
            AudioCacheEntry("newest.mp3", 100, 3),
            AudioCacheEntry("oldest.mp3", 100, 1),
            AudioCacheEntry("middle.mp3", 100, 2),
        )
        val victims = selectAudioCacheEvictions(entries, limitBytes = 150)
        assertEquals(listOf("oldest.mp3", "middle.mp3"), victims.map { it.name })
    }
}
