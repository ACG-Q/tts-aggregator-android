package com.example.ttsaggregator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTransferTest {

    @Test
    fun `导入支持 url 竖线标签与分组`() {
        val drafts = parseNodeImport(
            """
            https://a.example.com|东京|默认
            bad-line
            https://b.example.com
            """.trimIndent(),
        )

        assertEquals(2, drafts.size)
        assertEquals("东京", drafts[0].label)
        assertEquals("默认", drafts[0].group)
        assertEquals("默认", drafts[1].group)
    }

    @Test
    fun `导出不包含 API Key`() {
        val json = exportNodesJson(
            nodes = listOf(
                TtsNode(
                    baseUrl = "https://a",
                    label = "A",
                    group = "离线",
                    apiKey = "secret",
                ),
            ),
            config = AppSettings(),
            exportedAt = "2026-10-27T00:00:00Z",
        )

        assertFalse(json.contains("secret"))
        assertFalse(json.contains("apiKey"))
        assertTrue(json.contains("离线"))
        assertTrue(json.contains("\"count\": 1"))
    }

    @Test
    fun `导出的 JSON 可以重新导入`() {
        val json = exportNodesJson(
            nodes = listOf(
                TtsNode(baseUrl = "https://a", label = "东京", group = "备用"),
            ),
            config = AppSettings(),
            exportedAt = "2026-10-27T00:00:00Z",
        )

        val drafts = parseNodeImport(json)

        assertEquals(1, drafts.size)
        assertEquals("https://a", drafts[0].url)
        assertEquals("东京", drafts[0].label)
        assertEquals("备用", drafts[0].group)
    }
}
