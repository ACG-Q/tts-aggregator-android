package com.pulse.tts.tts

/**
 * 前台通知结束策略。
 *
 * 先立即取消一次，再在 350ms 与 2s 各兜底一次，覆盖部分 ROM
 * 在 stopForeground 之后才异步移除通知的行为。
 */
internal class NotificationCleanup(
    private val cancelNotification: () -> Unit,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
) {

    private var generation = 0L
    private var active = false
    private var retryScheduled = false

    fun begin() {
        synchronized(this) {
            generation += 1
            active = true
            retryScheduled = false
        }
    }

    fun finish() {
        val token: Long
        val shouldSchedule: Boolean
        synchronized(this) {
            active = false
            token = generation
            shouldSchedule = !retryScheduled
            retryScheduled = true
        }
        runCatching(cancelNotification)
        if (!shouldSchedule) return
        schedule(RETRY_DELAY_MS) { retry(token) }
        schedule(RETRY_DELAY_FINAL_MS) { retry(token) }
    }

    private fun retry(token: Long) {
        val shouldCancel = synchronized(this) {
            !active && generation == token
        }
        if (shouldCancel) runCatching(cancelNotification)
    }

    companion object {
        const val RETRY_DELAY_MS = 350L
        const val RETRY_DELAY_FINAL_MS = 2_000L
    }
}
