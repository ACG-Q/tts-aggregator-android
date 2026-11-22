package com.pulse.tts.tts

import android.os.SystemClock
import com.pulse.tts.data.CallMode
import com.pulse.tts.data.SynthesisEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class SystemReadingPhase { READING, COMPLETED, FAILED, STOPPED }

internal data class SystemReadingSnapshot(
    val sessionId: Long,
    val requestId: Long,
    val text: String,
    val phase: SystemReadingPhase,
    val mode: CallMode? = null,
    val nodeLabel: String? = null,
    val attempt: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val switchingTo: String? = null,
    val startedAtElapsedRealtime: Long,
)

internal class SystemReadingSessionCore(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {

    private val _snapshot = MutableStateFlow<SystemReadingSnapshot?>(null)
    val snapshot: StateFlow<SystemReadingSnapshot?> = _snapshot.asStateFlow()

    private var sessionCounter = 0L
    private var requestCounter = 0L
    private var lastRequestEndedAt: Long? = null
    private var lastError: String? = null
    private var completionJob: Job? = null

    fun onRequestStarted(text: String): Long {
        completionJob?.cancel()
        completionJob = null

        val now = clock()
        val current = _snapshot.value
        val withinGap = lastRequestEndedAt?.let { now - it <= SESSION_GAP_MS } ?: false
        val sameSession = current != null && (current.phase == SystemReadingPhase.READING || withinGap)
        val sessionId = if (sameSession) current!!.sessionId else ++sessionCounter
        val requestId = ++requestCounter
        lastError = null

        _snapshot.value = SystemReadingSnapshot(
            sessionId = sessionId,
            requestId = requestId,
            text = text,
            phase = SystemReadingPhase.READING,
            mode = current?.mode,
            nodeLabel = current?.nodeLabel,
            attempt = current?.attempt ?: 0,
            total = current?.total ?: 0,
            switchingTo = null,
            startedAtElapsedRealtime = now,
        )
        return requestId
    }

    fun onEvent(requestId: Long, event: SynthesisEvent) {
        val current = _snapshot.value ?: return
        if (current.requestId != requestId) return

        val next = when (event) {
            is SynthesisEvent.AttemptStarted -> current.copy(
                mode = event.mode,
                nodeLabel = event.node.label.ifBlank { event.node.baseUrl },
                attempt = event.attempt,
                total = event.total,
                error = null,
                switchingTo = null,
            )
            is SynthesisEvent.AttemptFailed -> current.copy(
                mode = event.mode,
                nodeLabel = event.node.label.ifBlank { event.node.baseUrl },
                error = event.error,
                switchingTo = if (event.willRetry) "正在切换到下一个节点" else null,
            )
            is SynthesisEvent.Succeeded -> current.copy(
                mode = event.mode,
                nodeLabel = event.node.label.ifBlank { event.node.baseUrl },
                error = null,
                switchingTo = null,
            )
            is SynthesisEvent.Failed -> current.copy(
                mode = event.mode ?: current.mode,
                error = event.error,
                switchingTo = null,
            )
            SynthesisEvent.Offline -> current.copy(
                error = "当前网络不可用，已暂停节点切换",
                switchingTo = null,
            )
        }
        lastError = next.error
        _snapshot.value = next
    }

    fun onTerminal(requestId: Long, ok: Boolean) {
        val current = _snapshot.value ?: return
        if (current.requestId != requestId || current.phase != SystemReadingPhase.READING) return

        if (!ok) {
            lastRequestEndedAt = clock()
            _snapshot.value = current.copy(
                phase = SystemReadingPhase.FAILED,
                error = lastError ?: "合成失败",
                switchingTo = null,
            )
            return
        }

        completionJob?.cancel()
        completionJob = scope.launch {
            delay(COMPLETION_DELAY_MS)
            val latest = _snapshot.value ?: return@launch
            if (latest.requestId == requestId && latest.phase == SystemReadingPhase.READING) {
                lastRequestEndedAt = clock()
                _snapshot.value = latest.copy(
                    phase = SystemReadingPhase.COMPLETED,
                    switchingTo = null,
                )
            }
        }
    }

    fun onStopped() {
        completionJob?.cancel()
        completionJob = null
        val current = _snapshot.value ?: return
        lastRequestEndedAt = clock()
        requestCounter += 1
        _snapshot.value = current.copy(
            requestId = requestCounter,
            phase = SystemReadingPhase.STOPPED,
            switchingTo = null,
        )
    }

    companion object {
        const val SESSION_GAP_MS = 1_500L
        const val COMPLETION_DELAY_MS = 1_500L
    }
}

internal object SystemReadingSession {

    private val core = SystemReadingSessionCore(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        clock = { SystemClock.elapsedRealtime() },
    )

    val snapshot: StateFlow<SystemReadingSnapshot?> = core.snapshot

    fun onRequestStarted(text: String): Long = core.onRequestStarted(text)

    fun onEvent(requestId: Long, event: SynthesisEvent) = core.onEvent(requestId, event)

    fun onTerminal(requestId: Long, ok: Boolean) = core.onTerminal(requestId, ok)

    fun onStopped() = core.onStopped()
}
