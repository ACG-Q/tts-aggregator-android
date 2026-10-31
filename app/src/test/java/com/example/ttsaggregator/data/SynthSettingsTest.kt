package com.example.ttsaggregator.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SynthSettingsTest {

    @Test
    fun `旧配置缺少 style 字段时按空字符串解码`() {
        val legacy = """{"rate":10,"pitch":-5,"voice":"zh-CN-XiaoxiaoNeural"}"""

        val decoded = AppJson.decodeFromString(SynthSettings.serializer(), legacy)

        assertEquals(10, decoded.rate)
        assertEquals(-5, decoded.pitch)
        assertEquals("zh-CN-XiaoxiaoNeural", decoded.voice)
        assertEquals("", decoded.style)
    }

    @Test
    fun `风格可持久化并往返解码`() {
        val settings = SynthSettings(voice = "zh-CN-XiaoxiaoNeural", style = "cheerful")

        val raw = AppJson.encodeToString(SynthSettings.serializer(), settings)
        val decoded = AppJson.decodeFromString(SynthSettings.serializer(), raw)

        assertEquals(settings, decoded)
    }
}
