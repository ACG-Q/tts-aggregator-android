package com.pulse.tts.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 节点健康状态归并：所有失败/成功计数都在互斥锁内“读最新 → 计算 → 写回”，
 * 避免并发合成时读改写丢计数。
 */
internal class NodeHealthRegistry(
    private val read: (String) -> TtsNode?,
    private val write: (TtsNode) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val failThreshold: () -> Int = { COOLDOWN_AFTER_FAILURES },
    private val cooldownMs: () -> Long = { COOLDOWN_MS },
) {

    private val mutex = Mutex()

    suspend fun onSuccess(node: TtsNode) {
        mutex.withLock {
            val current = read(node.id) ?: node
            if (current.failCount != 0 || current.cooldownUntil != 0L) {
                write(current.copy(failCount = 0, cooldownUntil = 0))
            }
        }
    }

    suspend fun onFailure(node: TtsNode, mode: CallMode): AttemptState = mutex.withLock {
        val current = read(node.id) ?: node
        val failCount = current.failCount + 1
        val triggeredCooldown = mode != CallMode.MANUAL && failCount >= failThreshold()
        val cooldownUntil =
            if (triggeredCooldown) clock() + cooldownMs() else current.cooldownUntil
        write(current.copy(failCount = failCount, cooldownUntil = cooldownUntil))
        AttemptState(failCount, cooldownUntil, triggeredCooldown)
    }

    companion object {
        const val COOLDOWN_AFTER_FAILURES = 3
        const val COOLDOWN_MS = 10 * 60 * 1000L
    }
}

internal data class AttemptState(
    val failCount: Int,
    val cooldownUntil: Long,
    val triggeredCooldown: Boolean,
)
