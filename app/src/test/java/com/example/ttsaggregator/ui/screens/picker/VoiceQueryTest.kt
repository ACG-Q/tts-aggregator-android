package com.example.ttsaggregator.ui.screens.picker

import com.example.ttsaggregator.data.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceQueryTest {

    private val voices = listOf(
        Voice(
            id = "zh-CN-XiaoxiaoNeural",
            name = "Xiaoxiao",
            localName = "晓晓",
            nameZh = "晓晓",
            gender = "Female",
            locale = "zh-CN",
        ),
        Voice(
            id = "zh-HK-HiuMaanNeural",
            name = "HiuMaan",
            nameZh = "曉曼",
            gender = "Female",
            locale = "zh-HK",
        ),
        Voice(id = "en-US-AriaNeural", name = "Aria", gender = "Female", locale = "en-US"),
        Voice(id = "ja-JP-NanamiNeural", name = "Nanami", nameZh = "七海", gender = "Female", locale = "ja-JP"),
    )

    @Test
    fun `按中文名搜索`() {
        val result = searchVoices(voices, "晓晓")

        assertEquals(listOf("zh-CN-XiaoxiaoNeural"), result.map { it.id })
    }

    @Test
    fun `按英文名与 id 搜索`() {
        assertEquals(listOf("en-US-AriaNeural"), searchVoices(voices, "ARIA").map { it.id })
        assertEquals(listOf("zh-CN-XiaoxiaoNeural"), searchVoices(voices, "xiaoxiao").map { it.id })
    }

    @Test
    fun `按语言与地区标签搜索`() {
        assertEquals(listOf("ja-JP-NanamiNeural"), searchVoices(voices, "日语").map { it.id })
        assertEquals(listOf("zh-HK-HiuMaanNeural"), searchVoices(voices, "中国香港").map { it.id })
    }

    @Test
    fun `空查询返回全部`() {
        assertEquals(voices, searchVoices(voices, "  "))
    }

    @Test
    fun `搜索结果中文优先`() {
        val result = searchVoices(voices, "neural")

        assertTrue(result.first().locale.startsWith("zh"))
    }

    @Test
    fun `中文筛选只保留中文声音`() {
        val result = filterVoices(voices, VoiceFilter.Chinese)

        assertEquals(listOf("zh-CN-XiaoxiaoNeural", "zh-HK-HiuMaanNeural"), result.map { it.id })
    }

    @Test
    fun `语言选项中文置顶且按数量排序`() {
        val options = languageOptions(voices)

        assertEquals("zh", options.first().first)
        assertEquals(2, options.first().second)
    }

    @Test
    fun `全部模式按语言分组且中文组第一`() {
        val groups = groupVoicesByLanguage(voices)

        assertEquals("zh", groups.first().first)
        assertEquals(2, groups.first().second.size)
    }
}
