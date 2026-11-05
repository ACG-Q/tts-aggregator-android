package com.pulse.tts.ui.util

enum class EngineStatus {
    ACTIVE,
    READY,
    DISABLED,
}

fun engineStatus(isDefaultEngine: Boolean, serviceRunning: Boolean): EngineStatus = when {
    !isDefaultEngine -> EngineStatus.DISABLED
    serviceRunning -> EngineStatus.ACTIVE
    else -> EngineStatus.READY
}

fun engineStatusLabel(status: EngineStatus): String = when (status) {
    EngineStatus.ACTIVE -> "服务运行中"
    EngineStatus.READY -> "引擎就绪"
    EngineStatus.DISABLED -> "未启用"
}
