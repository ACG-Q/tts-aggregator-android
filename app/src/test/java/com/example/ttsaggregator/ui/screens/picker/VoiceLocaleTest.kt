package com.example.ttsaggregator.ui.screens.picker

import com.example.ttsaggregator.data.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceLocaleTest {

    private fun voice(id: String, locale: String) = Voice(id = id, locale = locale)

    @Test
    fun `普通 locale 拆出语言与地区`() {
        val v = voice("zh-CN-XiaoxiaoNeural", "zh-CN")
        assertEquals("zh", v.localeLang())
        assertEquals("CN", v.localeRegion())
    }

    @Test
    fun `四字母脚本子标签不当作地区`() {
        val v = voice("iu-Cans-CA-InuktitutNeural", "iu-Cans-CA")
        assertEquals("iu", v.localeLang())
        assertEquals("CA", v.localeRegion())
    }

    @Test
    fun `方言后缀仍归入国家地区`() {
        val v = voice("zh-CN-guangxi-YunxiNeural", "zh-CN-guangxi")
        assertEquals("zh", v.localeLang())
        assertEquals("CN", v.localeRegion())
    }

    @Test
    fun `locale 为空时回退解析 id`() {
        val v = voice("en-US-JennyNeural", "")
        assertEquals("en", v.localeLang())
        assertEquals("US", v.localeRegion())
    }

    @Test
    fun `缺少地区段时返回 null`() {
        val v = voice("eo", "eo")
        assertEquals("eo", v.localeLang())
        assertNull(v.localeRegion())
    }
}
