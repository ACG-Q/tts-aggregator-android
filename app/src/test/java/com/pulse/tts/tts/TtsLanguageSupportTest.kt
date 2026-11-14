package com.pulse.tts.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TtsLanguageSupportTest {

    @Test
    fun `语言码缺失时返回不支持`() {
        assertEquals(
            TtsLanguageSupport.LANG_NOT_SUPPORTED,
            TtsLanguageSupport.availabilityOf(null, "CHN", ""),
        )
        assertEquals(
            TtsLanguageSupport.LANG_NOT_SUPPORTED,
            TtsLanguageSupport.availabilityOf("", "CHN", ""),
        )
    }

    @Test
    fun `只有语言码时返回 LANG_AVAILABLE`() {
        assertEquals(
            TtsLanguageSupport.LANG_AVAILABLE,
            TtsLanguageSupport.availabilityOf("zho", null, null),
        )
        assertEquals(
            TtsLanguageSupport.LANG_AVAILABLE,
            TtsLanguageSupport.availabilityOf("zho", "", ""),
        )
    }

    @Test
    fun `带国家码时返回 LANG_COUNTRY_AVAILABLE`() {
        assertEquals(
            TtsLanguageSupport.LANG_COUNTRY_AVAILABLE,
            TtsLanguageSupport.availabilityOf("zho", "CHN", ""),
        )
    }

    @Test
    fun `带变体时返回 LANG_COUNTRY_VAR_AVAILABLE`() {
        assertEquals(
            TtsLanguageSupport.LANG_COUNTRY_VAR_AVAILABLE,
            TtsLanguageSupport.availabilityOf("zho", "CHN", "PINYIN"),
        )
    }

    @Test
    fun `默认音色 zh_CN 的可用级别与框架预期一致`() {
        // 框架对 Locale("zh","CN") 期望 LANG_COUNTRY_AVAILABLE，
        // 不一致会导致系统设置判定默认语言不可用
        assertEquals(
            TtsLanguageSupport.LANG_COUNTRY_AVAILABLE,
            TtsLanguageSupport.availabilityOf("zho", "CHN", ""),
        )
        assertEquals("zh-CN", TtsLanguageSupport.VOICE_NAME)
    }

    @Test
    fun `可用语言列表按 ISO3 生成并去重`() {
        val tags = TtsLanguageSupport.availableVoiceTags(
            listOf(Locale.SIMPLIFIED_CHINESE, Locale.SIMPLIFIED_CHINESE, Locale.US),
        )
        assertEquals(listOf("zho-CHN", "eng-USA"), tags)
    }

    @Test
    fun `可用语言列表保留变体信息`() {
        val tags = TtsLanguageSupport.availableVoiceTags(listOf(Locale("en", "US", "POSIX")))
        assertEquals(listOf("eng-USA-POSIX"), tags)
    }
}
