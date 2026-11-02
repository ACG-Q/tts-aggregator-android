package com.example.ttsaggregator.ui.util

import com.example.ttsaggregator.data.Voice
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceLabelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val xiaoxiao = Voice(
        id = "zh-CN-XiaoxiaoNeural",
        name = "Xiaoxiao",
        localName = "晓晓",
        nameZh = "晓晓",
        gender = "Female",
        locale = "zh-CN",
        styles = listOf("cheerful", "newscast"),
    )
    private val xiaoxiaoHd = Voice(
        id = "zh-CN-Xiaoxiao:DragonHDFlashLatestNeural",
        name = "Xiaoxiao Dragon HD Flash Latest",
        nameZh = "晓晓",
        gender = "Female",
        locale = "zh-CN",
        styles = listOf("cheerful"),
    )
    private val xiaoxiaoDialects = Voice(
        id = "zh-CN-XiaoxiaoDialectsNeural",
        name = "Xiaoxiao Dialects",
        localName = "晓晓 方言",
        nameZh = "晓晓",
        gender = "Female",
        locale = "zh-CN",
    )
    private val xiaoxiaoMultilingual = Voice(
        id = "zh-CN-XiaoxiaoMultilingualNeural",
        name = "Xiaoxiao Multilingual",
        localName = "晓晓 多语言",
        nameZh = "晓晓",
        gender = "Female",
        locale = "zh-CN",
        styles = listOf("cheerful"),
    )

    private val xiaoxiaoGroup =
        listOf(xiaoxiao, xiaoxiaoHd, xiaoxiaoDialects, xiaoxiaoMultilingual)

    private val aria = Voice(
        id = "en-US-AriaNeural",
        name = "Aria",
        nameZh = "阿丽亚",
        gender = "Female",
        locale = "en-US",
        styles = listOf("cheerful"),
    )

    private val willemAf = Voice(
        id = "af-ZA-WillemNeural",
        name = "Willem",
        nameZh = "威廉",
        gender = "Male",
        locale = "af-ZA",
    )
    private val williamAu = Voice(
        id = "en-AU-WilliamNeural",
        name = "William",
        nameZh = "威廉",
        gender = "Male",
        locale = "en-AU",
    )

    @Test
    fun `唯一声音不加括号`() {
        assertEquals("阿丽亚", voiceLabel(listOf(aria), aria))
    }

    @Test
    fun `晓晓四个变体互相区分`() {
        val labels = xiaoxiaoGroup.map { voiceLabel(xiaoxiaoGroup, it) }

        assertEquals(
            listOf("晓晓（标准）", "晓晓（HD Flash）", "晓晓（方言）", "晓晓（多语言）"),
            labels,
        )
    }

    @Test
    fun `风格追加在变体之后`() {
        val name = voiceDisplayName(
            xiaoxiaoGroup,
            xiaoxiaoMultilingual.id,
            "cheerful",
        )

        assertEquals("晓晓（多语言）·愉悦", name)
    }

    @Test
    fun `唯一声音直接追加风格`() {
        assertEquals("阿丽亚·愉悦", voiceDisplayName(listOf(aria), aria.id, "cheerful"))
    }

    @Test
    fun `风格不在声音列表时不追加`() {
        assertEquals("阿丽亚", voiceDisplayName(listOf(aria), aria.id, "newscast"))
    }

    @Test
    fun `空 id 返回默认音色`() {
        assertEquals("默认音色", voiceDisplayName(emptyList(), ""))
    }

    @Test
    fun `跨语言同名用语言区分`() {
        val voices = listOf(willemAf, williamAu)

        val labels = voices.map { voiceLabel(voices, it) }

        assertEquals("威廉（南非荷兰语）", labels[0])
        assertEquals("威廉（英语）", labels[1])
        assertNotEquals(labels[0], labels[1])
    }

    @Test
    fun `列表未加载时降级并保留风格`() {
        assertEquals(
            "中文 · 中国 · 简体 · Xiaoxiao·愉悦",
            voiceDisplayName(emptyList(), "zh-CN-XiaoxiaoNeural", "cheerful"),
        )
    }

    @Test
    fun `全量声音数据同名组标签唯一`() {
        val file = File("src/main/assets/voices.json")
        if (!file.exists()) return
        val voices = json
            .decodeFromString<List<Voice>>(file.readText())

        val duplicates = voices.groupBy { it.displayName }.filterValues { it.size > 1 }
        assertTrue(duplicates.isNotEmpty())
        duplicates.forEach { (name, group) ->
            val labels = group.map { voiceLabel(voices, it) }
            assertEquals("同名组 [$name] 的标签应唯一", group.size, labels.toSet().size)
        }
    }
}
