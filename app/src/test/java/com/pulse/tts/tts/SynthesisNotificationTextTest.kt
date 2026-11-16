package com.pulse.tts.tts

import com.pulse.tts.data.CallMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthesisNotificationTextTest {

    @Test
    fun `selecting node shows mode and preview`() {
        val content = buildNotificationContent(
            NotificationState(
                text = "这是一段测试文本",
                mode = CallMode.GROUP,
            ),
        )

        assertEquals("正在选择节点…", content.title)
        assertEquals("文本：这是一段测试文本", content.body)
        assertEquals("轮询", content.subText)
    }

    @Test
    fun `attempt shows node progress and elapsed`() {
        val content = buildNotificationContent(
            NotificationState(
                text = "你好",
                mode = CallMode.GROUP,
                nodeLabel = "节点 A",
                attempt = 2,
                total = 5,
                elapsedMs = 1_200,
            ),
        )

        assertEquals("正在朗读 · 节点 A", content.title)
        assertEquals("文本：你好", content.body)
        assertEquals("轮询 · 第 2/5 个节点 · 已用 1.2 秒", content.subText)
    }

    @Test
    fun `switching node and failure reason are visible`() {
        val switching = buildNotificationContent(
            NotificationState(
                text = "你好",
                mode = CallMode.AUTO,
                nodeLabel = "节点 A",
                attempt = 1,
                total = 3,
                switchingTo = "节点 B",
            ),
        )
        assertTrue(switching.subText.contains("正在切换到 节点 B"))

        val failed = buildNotificationContent(
            NotificationState(text = "你好", error = "当前网络不可用"),
        )
        assertEquals("朗读失败", failed.title)
        assertTrue(failed.subText.contains("当前网络不可用"))
    }

    @Test
    fun `long text is truncated`() {
        val content = buildNotificationContent(
            NotificationState(text = "一".repeat(60)),
        )

        assertEquals("文本：${"一".repeat(40)}…", content.body)
    }
}
