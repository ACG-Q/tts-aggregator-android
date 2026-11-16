package com.pulse.tts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeMergeTest {

    private fun node(
        id: String = "id",
        health: TtsNode.Health = TtsNode.Health.UNKNOWN,
        label: String = "",
        apiKey: String? = null,
        disabled: Boolean = false,
        cooldownUntil: Long = 0,
        failCount: Int = 0,
        voiceCount: Int = 0,
        lastChecked: Long = 0,
        group: String = "默认",
        lastSpeedMs: Long = 0,
        lastSpeedAt: Long = 0,
    ) = TtsNode(
        id = id,
        baseUrl = "http://n",
        label = label,
        apiKey = apiKey,
        health = health,
        disabled = disabled,
        cooldownUntil = cooldownUntil,
        failCount = failCount,
        voiceCount = voiceCount,
        lastChecked = lastChecked,
        group = group,
        lastSpeedMs = lastSpeedMs,
        lastSpeedAt = lastSpeedAt,
    )

    @Test
    fun `探测成功清除冷却与失败计数并保留用户配置`() {
        val previous = node(
            id = "keep",
            label = "旧标签",
            apiKey = "k",
            disabled = true,
            cooldownUntil = 123,
            failCount = 2,
            lastSpeedMs = 320,
            lastSpeedAt = 456,
        )
        val probed = node(
            id = "new",
            health = TtsNode.Health.OK,
            label = "",
            apiKey = null,
            voiceCount = 7,
            lastChecked = 999,
        )

        val merged = probed.mergeProbe(previous)

        assertEquals("keep", merged.id)
        assertEquals("旧标签", merged.label)
        assertEquals("k", merged.apiKey)
        assertTrue(merged.disabled)
        assertEquals(0L, merged.cooldownUntil)
        assertEquals(0, merged.failCount)
        assertEquals(TtsNode.Health.OK, merged.health)
        assertEquals(7, merged.voiceCount)
        assertEquals(999L, merged.lastChecked)
        assertEquals(320L, merged.lastSpeedMs)
        assertEquals(456L, merged.lastSpeedAt)
    }

    @Test
    fun `探测失败保留冷却与失败计数`() {
        val previous = node(id = "keep", cooldownUntil = 123, failCount = 2)
        val probed = node(id = "new", health = TtsNode.Health.FAIL)

        val merged = probed.mergeProbe(previous)

        assertEquals(123L, merged.cooldownUntil)
        assertEquals(2, merged.failCount)
    }

    @Test
    fun `探测结果保留原分组`() {
        val previous = node(id = "keep", group = "离线")
        val probed = node(id = "new", health = TtsNode.Health.OK, group = "默认")

        val merged = probed.mergeProbe(previous)

        assertEquals("离线", merged.group)
    }

    @Test
    fun `authUrl 仅在提供 Key 时挂 api_key`() {
        val signed = authUrl("http://n/api/v1/health", "abc")
        assertEquals("abc", signed?.queryParameter("api_key"))

        val unsigned = authUrl("http://n/api/v1/health", null)
        assertNull(unsigned?.queryParameter("api_key"))
        assertNull(authUrl("not-a-url", "abc"))
    }
}
