package com.example.ttsaggregator.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMergeTest {

    private fun voice(
        id: String,
        name: String,
        nameZh: String = "",
        introZh: String = "",
    ) = Voice(
        id = id,
        name = name,
        localName = name,
        nameZh = nameZh,
        introZh = introZh,
    )

    @Test
    fun `按 short_name 合并内置中文翻译`() {
        val builtin = listOf(voice("zh-CN-XiaoxiaoNeural", "Xiaoxiao", "晓晓", "中文女声"))
        val remote = listOf(voice("zh-CN-XiaoxiaoNeural", "Xiaoxiao"))

        val merged = mergeVoiceTranslations(remote, builtin)

        assertEquals("晓晓", merged.single().nameZh)
        assertEquals("中文女声", merged.single().introZh)
    }

    @Test
    fun `id 变化时按基础人名回退匹配`() {
        val builtin = listOf(voice("en-US-JennyNeural", "Jenny Dragon HD Latest", "珍妮", "英语女声"))
        val remote = listOf(voice("en-US-Jenny:DragonHDLatestNeural", "Jenny Dragon HD Latest"))

        val merged = mergeVoiceTranslations(remote, builtin)

        assertEquals("珍妮", merged.single().nameZh)
        assertEquals("英语女声", merged.single().introZh)
    }

    @Test
    fun `网络已带翻译时优先保留网络值`() {
        val builtin = listOf(voice("en-US-AvaNeural", "Ava", "艾娃", "内置简介"))
        val remote = listOf(voice("en-US-AvaNeural", "Ava", "阿瓦", "网络简介"))

        val merged = mergeVoiceTranslations(remote, builtin)

        assertEquals("阿瓦", merged.single().nameZh)
        assertEquals("网络简介", merged.single().introZh)
    }

    @Test
    fun `没有内置数据时原样返回`() {
        val remote = listOf(voice("xx-XX-TestNeural", "Test"))

        val merged = mergeVoiceTranslations(remote, emptyList())

        assertEquals(remote, merged)
    }
}
