package com.pulse.tts.ui.permissions

enum class PermissionKey {
    NOTIFY,
    BATTERY,
    BACKGROUND,
    NETWORK,
    TTS,
    FILES,
}

data class PermissionState(
    val key: PermissionKey,
    val granted: Boolean,
    val statusText: String = "",
    val detail: String = "",
    val readOnly: Boolean = false,
)

private val CRITICAL = setOf(
    PermissionKey.NOTIFY,
    PermissionKey.BATTERY,
    PermissionKey.BACKGROUND,
)

fun pendingCriticalCount(states: List<PermissionState>): Int =
    states.count { it.key in CRITICAL && !it.granted && !it.readOnly }

fun firstPendingIndex(states: List<PermissionState>): Int? =
    states.indexOfFirst { it.key in CRITICAL && !it.granted && !it.readOnly }
        .takeIf { it >= 0 }

fun permissionBadgeText(states: List<PermissionState>): String {
    val pending = pendingCriticalCount(states)
    return if (pending == 0) "全部已就绪" else "$pending 项待处理"
}
