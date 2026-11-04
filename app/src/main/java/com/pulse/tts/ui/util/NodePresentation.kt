package com.pulse.tts.ui.util

import com.pulse.tts.data.TtsNode
import java.util.Locale

enum class NodeState { AVAILABLE, UNTESTED, COOLING, FAILED, DISABLED }

data class NodeSummary(val available: Int, val cooling: Int, val disabled: Int)

/** 添加节点表单校验：返回首个错误文案，通过则返回 null。 */
fun validateNodeDraft(name: String, url: String): String? {
    val trimmedUrl = url.trim()
    return when {
        name.isBlank() -> "给节点起个名字"
        !Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(trimmedUrl) ->
            "请输入有效的 http(s) 地址"
        else -> null
    }
}

fun TtsNode.uiState(now: Long = System.currentTimeMillis()): NodeState = when {
    disabled -> NodeState.DISABLED
    isCoolingDown(now) -> NodeState.COOLING
    health == TtsNode.Health.FAIL -> NodeState.FAILED
    health == TtsNode.Health.UNKNOWN -> NodeState.UNTESTED
    else -> NodeState.AVAILABLE
}

fun List<TtsNode>.nodeSummary(now: Long = System.currentTimeMillis()): NodeSummary = NodeSummary(
    available = count { !it.disabled && it.health != TtsNode.Health.FAIL && !it.isCoolingDown(now) },
    cooling = count { !it.disabled && (it.isCoolingDown(now) || it.health == TtsNode.Health.FAIL) },
    disabled = count { it.disabled },
)

fun relativeTimeLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "尚未测试"
    val seconds = ((now - timestamp).coerceAtLeast(0L) / 1000L)
    return when {
        seconds < 5 -> "刚刚"
        seconds < 60 -> "$seconds 秒前"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3_600} 小时前"
        else -> "${seconds / 86_400} 天前"
    }
}

fun cooldownRemainingLabel(until: Long, now: Long = System.currentTimeMillis()): String {
    val remain = ((until - now).coerceAtLeast(0L) / 1000L)
    return String.format(Locale.ROOT, "剩余 %02d:%02d", remain / 60, remain % 60)
}

fun TtsNode.statusLabel(now: Long = System.currentTimeMillis()): String = when (uiState(now)) {
    NodeState.AVAILABLE -> "可用"
    NodeState.UNTESTED -> "未测试"
    NodeState.COOLING -> cooldownRemainingLabel(cooldownUntil, now)
    NodeState.FAILED -> "连接异常"
    NodeState.DISABLED -> "已停用"
}

fun TtsNode.secondaryLabel(now: Long = System.currentTimeMillis()): String {
    val testTime = relativeTimeLabel(lastChecked, now)
    return when (uiState(now)) {
        NodeState.COOLING -> "失败 ${failCount.coerceAtLeast(1)} 次 · $testTime"
        NodeState.FAILED -> "失败 ${failCount.coerceAtLeast(1)} 次 · $testTime"
        else -> "上次测试 $testTime"
    }
}

fun TtsNode.badgeLabel(
    now: Long = System.currentTimeMillis(),
    isPrimary: Boolean = false,
    isGroupMember: Boolean = false,
): String = when {
    disabled -> "停用"
    isCoolingDown(now) -> "冷却"
    health == TtsNode.Health.FAIL -> "异常"
    health == TtsNode.Health.UNKNOWN -> "未测试"
    isPrimary -> "主用"
    isGroupMember -> "成员"
    else -> "备用"
}
