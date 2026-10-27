package com.example.ttsaggregator.data

val DEFAULT_NODE_GROUPS = listOf("默认", "离线", "备用")

enum class NodeFilter(val label: String) {
    ALL("全部"),
    OK("健康"),
    BAD("异常"),
    KEYLESS("无鉴权"),
    DISABLED("已停用");

    fun matches(node: TtsNode, now: Long = System.currentTimeMillis()): Boolean = when (this) {
        ALL -> true
        OK -> node.health == TtsNode.Health.OK && !node.disabled && !node.isCoolingDown(now)
        BAD -> node.health == TtsNode.Health.FAIL || node.isCoolingDown(now)
        KEYLESS -> node.keyless
        DISABLED -> node.disabled
    }
}

enum class NodeSort(val label: String) {
    DEFAULT("默认序"),
    NAME("名称"),
    LAST("最近测试"),
    FAIL("失败次数");

    fun next(): NodeSort = entries[(ordinal + 1) % entries.size]
}

fun filterAndSortNodes(
    nodes: List<TtsNode>,
    query: String,
    filter: NodeFilter,
    sort: NodeSort,
    now: Long = System.currentTimeMillis(),
): List<TtsNode> {
    val q = query.trim()
    val filtered = nodes.filter { node ->
        filter.matches(node, now) && (
            q.isEmpty() ||
                node.label.contains(q, ignoreCase = true) ||
                node.baseUrl.contains(q, ignoreCase = true) ||
                node.group.contains(q, ignoreCase = true)
            )
    }
    return when (sort) {
        NodeSort.DEFAULT -> filtered.sortedWith(
            compareBy({ it.health.ordinal }, { -it.lastChecked }),
        )
        NodeSort.NAME -> filtered.sortedBy { it.label.ifBlank { it.baseUrl } }
        NodeSort.LAST -> filtered.sortedByDescending { it.lastChecked }
        NodeSort.FAIL -> filtered.sortedByDescending { it.failCount }
    }
}

fun knownGroups(nodes: List<TtsNode>): List<String> =
    (DEFAULT_NODE_GROUPS + nodes.map { it.group })
        .filter { it.isNotBlank() }
        .distinct()
