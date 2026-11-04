package com.pulse.tts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeOrderTest {

    private fun node(
        id: String,
        health: TtsNode.Health = TtsNode.Health.OK,
        lastChecked: Long = 0,
    ) = TtsNode(
        id = id,
        baseUrl = "http://$id",
        health = health,
        lastChecked = lastChecked,
    )

    @Test
    fun `upsert 替换同 id 并保持健康优先最近测试排序`() {
        val list = listOf(
            node("a", TtsNode.Health.OK, 100),
            node("b", TtsNode.Health.UNKNOWN, 900),
            node("c", TtsNode.Health.OK, 300),
        )

        val result = list.withUpserted(node("a", TtsNode.Health.FAIL, 50))

        assertEquals(listOf("c", "a", "b"), result.map { it.id })
        assertEquals(3, result.size)
        assertEquals(TtsNode.Health.FAIL, result.first { it.id == "a" }.health)
    }

    @Test
    fun `upsert 新节点按排序插入`() {
        val list = listOf(
            node("a", TtsNode.Health.OK, 100),
            node("b", TtsNode.Health.UNKNOWN, 900),
            node("c", TtsNode.Health.OK, 300),
        )

        val result = list.withUpserted(node("d", TtsNode.Health.OK, 500))

        assertEquals(listOf("d", "c", "a", "b"), result.map { it.id })
        assertEquals(4, result.size)
    }
}
