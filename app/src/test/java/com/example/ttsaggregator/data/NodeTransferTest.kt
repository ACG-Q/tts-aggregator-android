package com.example.ttsaggregator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `导入 JSON 时带回偏好设置`() {
        val json = exportNodesJson(
            nodes = listOf(TtsNode(baseUrl = "https://a")),
            config = AppSettings(
                theme = ThemeMode.DARK,
                cooldownMinutes = 30,
                failThreshold = 5,
                sleepMinutes = 15,
            ),
            exportedAt = "2026-10-27T00:00:00Z",
        )

        val bundle = parseNodeBundle(json)

        assertEquals(1, bundle.drafts.size)
        val restored = requireNotNull(bundle.settings)
        assertEquals(ThemeMode.DARK, restored.theme)
        assertEquals(30, restored.cooldownMinutes)
        assertEquals(5, restored.failThreshold)
        assertEquals(15, restored.sleepMinutes)
    }

    @Test
    fun `导入纯文本清单不覆盖偏好设置`() {
        val bundle = parseNodeBundle("https://a|东京|默认")

        assertEquals(1, bundle.drafts.size)
        assertNull(bundle.settings)
    }

    @Test
    fun `导入非法 JSON 返回空结果且不还原设置`() {
        val bundle = parseNodeBundle("{\"nodes\": 1}")

        assertTrue(bundle.drafts.isEmpty())
        assertNull(bundle.settings)
    }
}
