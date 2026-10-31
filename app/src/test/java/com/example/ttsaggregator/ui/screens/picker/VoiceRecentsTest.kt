package com.example.ttsaggregator.ui.screens.picker

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRecentsTest {

    @Test
    fun `新应用的声音排在最前并去重`() {
        val result = withRecent(listOf("a", "b", "c"), "b")

        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun `最近记录最多六条`() {
        val stored = listOf("1", "2", "3", "4", "5", "6")

        assertEquals(listOf("7", "1", "2", "3", "4", "5"), withRecent(stored, "7"))
    }

    @Test
    fun `当前声音并入最近并过滤失效 id`() {
        val result = recentVoiceIds(
            currentId = "current",
            stored = listOf("old", "gone", "current2"),
            available = setOf("current", "old", "current2"),
        )

        assertEquals(listOf("current", "old", "current2"), result)
    }

    @Test
    fun `当前声音为空且无记录时返回空列表`() {
        assertEquals(emptyList<String>(), recentVoiceIds("", emptyList(), emptySet()))
    }
}
