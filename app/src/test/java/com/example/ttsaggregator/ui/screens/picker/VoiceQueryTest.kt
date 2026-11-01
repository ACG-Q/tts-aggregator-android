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

    @Test
    fun `常用筛选按最近顺序返回且忽略其他分类`() {
        val recent = listOf("ja-JP-NanamiNeural", "zh-CN-XiaoxiaoNeural")
        val result = filterVoices(voices, VoiceFilter.Frequent, recent)

        assertEquals(recent, result.map { it.id })
    }

    @Test
    fun `常用筛选过滤已不存在的声音`() {
        val result = filterVoices(voices, VoiceFilter.Frequent, listOf("gone", "en-US-AriaNeural"))

        assertEquals(listOf("en-US-AriaNeural"), result.map { it.id })
    }

    @Test
    fun `默认筛选有记录为常用无记录为中文`() {
        assertEquals(VoiceFilter.Chinese, defaultFilter(emptyList()))
        assertEquals(VoiceFilter.Frequent, defaultFilter(listOf("zh-CN-XiaoxiaoNeural")))
    }

    @Test
    fun `chips 顺序固定为常用全部中文英语其余语言`() {
        val entries = chipEntries(languageOptions(voices))

        assertEquals(
            listOf("常用", "全部", "中文", "英语", "日语"),
            entries.map { it.label },
        )
        assertEquals(VoiceFilter.Frequent, entries.first().filter)
    }

    @Test
    fun `chips 全部放下时不显示更多`() {
        val plan = planChips(listOf(40f, 40f, 40f), available = 200f, spacing = 10f, moreWidth = 50f)

        assertEquals(ChipPlan(3, false), plan)
    }

    @Test
    fun `chips 放不下时保留前缀并标记更多`() {
        val plan = planChips(listOf(60f, 60f, 60f, 60f), available = 220f, spacing = 10f, moreWidth = 50f)

        assertEquals(ChipPlan(2, true), plan)
    }

    @Test
    fun `chips 仅剩更多也放不下时仍标记溢出`() {
        val plan = planChips(listOf(200f), available = 100f, spacing = 10f, moreWidth = 90f)

        assertEquals(ChipPlan(0, true), plan)
    }

    @Test
    fun `chips 空列表无溢出`() {
        assertEquals(ChipPlan(0, false), planChips(emptyList(), 100f, 10f, 50f))
    }
}
