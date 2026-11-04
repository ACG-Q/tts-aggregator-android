package com.pulse.tts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeQueryTest {

    private fun node(
        id: String,
        label: String = "",
        group: String = "默认",
        health: TtsNode.Health = TtsNode.Health.UNKNOWN,
        disabled: Boolean = false,
        lastChecked: Long = 0,
        failCount: Int = 0,
    ) = TtsNode(
        id = id,
        baseUrl = "http://$id",
        label = label,
        group = group,
        health = health,
        disabled = disabled,
        lastChecked = lastChecked,
        failCount = failCount,
    )

    @Test
    fun `搜索同时匹配名称 URL 与分组`() {
        val list = listOf(
            node("a", label = "香港", group = "默认"),
            node("b", label = "备用", group = "离线"),
        )

        assertEquals(
            listOf("b"),
            filterAndSortNodes(list, "离线", NodeFilter.ALL, NodeSort.DEFAULT).map { it.id },
        )
        assertEquals(
            listOf("a"),
            filterAndSortNodes(list, "香港", NodeFilter.ALL, NodeSort.DEFAULT).map { it.id },
        )
    }

    @Test
    fun `健康筛选排除停用节点`() {
        val list = listOf(
            node("ok", health = TtsNode.Health.OK),
            node("off", health = TtsNode.Health.OK, disabled = true),
        )

        assertEquals(
            listOf("ok"),
            filterAndSortNodes(list, "", NodeFilter.OK, NodeSort.DEFAULT).map { it.id },
        )
    }

    @Test
    fun `已知分组包含默认组且去重`() {
        val list = listOf(
            node("a", group = "离线"),
            node("b", group = "离线"),
        )

        assertEquals(listOf("默认", "离线", "备用"), knownGroups(list))
    }

    @Test
    fun `排序支持名称与失败次数`() {
        val list = listOf(
            node("a", label = "A", failCount = 1),
            node("b", label = "B", failCount = 5),
        )

        assertEquals(
            listOf("a", "b"),
            filterAndSortNodes(list, "", NodeFilter.ALL, NodeSort.NAME).map { it.id },
        )
        assertEquals(
            listOf("b", "a"),
            filterAndSortNodes(list, "", NodeFilter.ALL, NodeSort.FAIL).map { it.id },
        )
    }
}
