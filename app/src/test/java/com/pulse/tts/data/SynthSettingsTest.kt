package com.pulse.tts.data

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

    @Test
    fun `旧配置缺少 recentVoices 时按空列表解码`() {
        val legacy =
            """{"rate":0,"pitch":0,"voice":"zh-CN-XiaoxiaoNeural","style":"cheerful"}"""

        val decoded = AppJson.decodeFromString(SynthSettings.serializer(), legacy)

        assertEquals(emptyList<String>(), decoded.recentVoices)
    }

    @Test
    fun `最近声音可持久化并往返解码`() {
        val settings = SynthSettings(
            voice = "zh-CN-XiaoxiaoNeural",
            recentVoices = listOf("zh-CN-XiaoxiaoNeural", "en-US-AriaNeural"),
        )

        val raw = AppJson.encodeToString(SynthSettings.serializer(), settings)
        val decoded = AppJson.decodeFromString(SynthSettings.serializer(), raw)

        assertEquals(settings, decoded)
    }
}
