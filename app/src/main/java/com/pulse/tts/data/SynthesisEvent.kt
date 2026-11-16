package com.pulse.tts.data

/** 调度过程事件：供系统 TTS 通知等外部消费者展示进度。 */
sealed interface SynthesisEvent {
    val mode: CallMode?

    data class AttemptStarted(
        val node: TtsNode,
        val attempt: Int,
        val total: Int,
        override val mode: CallMode,
        val fromNode: String? = null,
    ) : SynthesisEvent

    data class AttemptFailed(
        val node: TtsNode,
        val error: String,
        val willRetry: Boolean,
        override val mode: CallMode,
    ) : SynthesisEvent

    data class Succeeded(
        val node: TtsNode,
        val elapsedMs: Long,
        override val mode: CallMode,
    ) : SynthesisEvent

    data class Failed(
        val error: String,
        override val mode: CallMode? = null,
    ) : SynthesisEvent

    data object Offline : SynthesisEvent {
        override val mode: CallMode? = null
    }
}
