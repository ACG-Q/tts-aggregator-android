package com.pulse.tts.ui.util

import com.pulse.tts.data.TtsNode
import org.junit.Assert.assertEquals
import org.junit.Test

class NodePresentationTest {

    private val now = 1_700_000_000_000L

    private fun node(
        health: TtsNode.Health = TtsNode.Health.OK,
        disabled: Boolean = false,
        cooldownUntil: Long = 0,
        failCount: Int = 0,
    ) = TtsNode(
        baseUrl = "https://tts.example.com",
        health = health,
        disabled = disabled,
        cooldownUntil = cooldownUntil,
        failCount = failCount,
    )

    @Test
    fun summary_countsUnknownAsAvailableAndFailureAsCooling() {
        val nodes = listOf(
            node(health = TtsNode.Health.OK),
            node(health = TtsNode.Health.UNKNOWN),
            node(health = TtsNode.Health.FAIL, failCount = 3),
            node(health = TtsNode.Health.FAIL, cooldownUntil = now + 60_000),
            node(disabled = true),
        )
        assertEquals(NodeSummary(available = 2, cooling = 2, disabled = 1), nodes.nodeSummary(now))
    }

    @Test
    fun cooldownState_winsOverFailedHealth() {
        assertEquals(NodeState.COOLING, node(health = TtsNode.Health.FAIL, cooldownUntil = now + 1).uiState(now))
    }

    @Test
    fun disabledState_winsOverCooldown() {
        val disabled = node(disabled = true, cooldownUntil = now + 60_000)
        assertEquals(NodeState.DISABLED, disabled.uiState(now))
    }

    @Test
    fun relativeTimeLabel_handlesMinuteHourDay() {
        assertEquals("刚刚", relativeTimeLabel(now - 2_000, now))
        assertEquals("12 秒前", relativeTimeLabel(now - 12_000, now))
        assertEquals("1 分钟前", relativeTimeLabel(now - 75_000, now))
        assertEquals("3 小时前", relativeTimeLabel(now - 3 * 3_600_000, now))
        assertEquals("2 天前", relativeTimeLabel(now - 2 * 86_400_000, now))
        assertEquals("尚未测试", relativeTimeLabel(0, now))
    }

    @Test
    fun cooldownRemainingLabel_formatsMinutesAndSeconds() {
        assertEquals("剩余 08:12", cooldownRemainingLabel(now + 492_000, now))
        assertEquals("剩余 00:00", cooldownRemainingLabel(now - 1, now))
    }

    @Test
    fun statusLabel_reflectsNodeState() {
        assertEquals("可用", node().statusLabel(now))
        assertEquals("未测试", node(health = TtsNode.Health.UNKNOWN).statusLabel(now))
        assertEquals("已停用", node(disabled = true).statusLabel(now))
        assertEquals("连接异常", node(health = TtsNode.Health.FAIL, failCount = 2).statusLabel(now))
        assertEquals(
            "剩余 08:12",
            node(health = TtsNode.Health.FAIL, cooldownUntil = now + 492_000).statusLabel(now),
        )
    }

    @Test
    fun badgeLabel_prefersDisabledThenCooldownThenConnectionFailure() {
        assertEquals("停用", node(disabled = true).badgeLabel(now))
        assertEquals("冷却", node(cooldownUntil = now + 60_000).badgeLabel(now))
        assertEquals("异常", node(health = TtsNode.Health.FAIL).badgeLabel(now))
        assertEquals("未测试", node(health = TtsNode.Health.UNKNOWN).badgeLabel(now))
        assertEquals("主用", node().badgeLabel(now, isPrimary = true))
        assertEquals("成员", node().badgeLabel(now, isGroupMember = true))
        assertEquals("备用", node().badgeLabel(now))
    }

    @Test
    fun validateNodeDraft_rejectsBlankName() {
        assertEquals("给节点起个名字", validateNodeDraft("  ", "https://tts.example.com"))
    }

    @Test
    fun validateNodeDraft_rejectsMissingSchemeOrSpaces() {
        assertEquals("请输入有效的 http(s) 地址", validateNodeDraft("东京", "tts.example.com"))
        assertEquals("请输入有效的 http(s) 地址", validateNodeDraft("东京", "ftp://tts.example.com"))
        assertEquals("请输入有效的 http(s) 地址", validateNodeDraft("东京", "https://tts example.com"))
    }

    @Test
    fun validateNodeDraft_acceptsHttpAndHttps() {
        assertEquals(null, validateNodeDraft("东京", "https://tts.example.com/v1"))
        assertEquals(null, validateNodeDraft("本地", "http://10.0.2.2:8766"))
    }
}
