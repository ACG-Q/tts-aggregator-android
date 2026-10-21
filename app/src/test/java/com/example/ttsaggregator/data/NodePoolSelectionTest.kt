package com.example.ttsaggregator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodePoolSelectionTest {

    private val now = 1_000_000L

    private fun node(
        id: String,
        health: TtsNode.Health = TtsNode.Health.OK,
        disabled: Boolean = false,
        cooldownUntil: Long = 0,
        lastChecked: Long = 0,
    ) = TtsNode(
        id = id,
        baseUrl = "http://$id",
        health = health,
        disabled = disabled,
        cooldownUntil = cooldownUntil,
        lastChecked = lastChecked,
    )

    @Test
    fun `自动候选排除停用节点`() {
        val list = listOf(node("a"), node("b", disabled = true))
        assertEquals(listOf("a"), selectAutoCandidates(list, now).map { it.id })
    }

    @Test
    fun `自动候选排除异常与冷却节点`() {
        val list = listOf(
            node("ok"),
            node("fail", health = TtsNode.Health.FAIL),
            node("cooling", cooldownUntil = now + 1000),
        )
        assertEquals(listOf("ok"), selectAutoCandidates(list, now).map { it.id })
    }

    @Test
    fun `自动候选健康优先 同健康度最近测试在前`() {
        val list = listOf(
            node("unknown", health = TtsNode.Health.UNKNOWN, lastChecked = 999),
            node("okOld", lastChecked = 100),
            node("okNew", lastChecked = 500),
        )
        assertEquals(listOf("okNew", "okOld", "unknown"), selectAutoCandidates(list, now).map { it.id })
    }

    @Test
    fun `组轮询排除停用节点`() {
        val list = listOf(node("a"), node("b", disabled = true), node("c"))
        assertEquals(listOf("a"), selectGroupCandidates(list, setOf("a", "b"), now).map { it.id })
    }

    @Test
    fun `组轮询保持原顺序并排除冷却节点`() {
        val list = listOf(
            node("c"),
            node("a", cooldownUntil = now + 1000),
            node("b"),
        )
        val result = selectGroupCandidates(list, setOf("a", "b", "c"), now)
        assertEquals(listOf("c", "b"), result.map { it.id })
    }

    @Test
    fun `组轮询忽略组外节点`() {
        val list = listOf(node("in"), node("out"))
        assertTrue(selectGroupCandidates(list, setOf("in"), now).none { it.id == "out" })
    }
}
