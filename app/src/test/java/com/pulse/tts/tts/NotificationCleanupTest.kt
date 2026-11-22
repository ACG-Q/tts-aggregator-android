package com.pulse.tts.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCleanupTest {

    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private var cancelCount = 0

    private fun cleanup() = NotificationCleanup(
        cancelNotification = { cancelCount += 1 },
        schedule = { delay, action -> scheduled += delay to action },
    )

    @Test
    fun `finish cancels immediately and schedules two retries`() {
        val cleanup = cleanup()
        cleanup.begin()
        cleanup.finish()

        assertEquals(1, cancelCount)
        assertEquals(listOf(350L, 2_000L), scheduled.map { it.first })
    }

    @Test
    fun `retries cancel again after delays`() {
        val cleanup = cleanup()
        cleanup.begin()
        cleanup.finish()

        scheduled.forEach { (_, action) -> action() }

        assertEquals(3, cancelCount)
    }

    @Test
    fun `begin invalidates previous retries`() {
        val cleanup = cleanup()
        cleanup.begin()
        cleanup.finish()
        cleanup.begin()

        scheduled.forEach { (_, action) -> action() }

        assertEquals(1, cancelCount)
    }

    @Test
    fun `second finish does not schedule more retries`() {
        val cleanup = cleanup()
        cleanup.begin()
        cleanup.finish()
        cleanup.finish()

        assertEquals(2, scheduled.size)
        assertTrue(scheduled.all { it.first >= 350L })
    }

    @Test
    fun `retry does nothing after begin`() {
        val cleanup = cleanup()
        cleanup.begin()
        cleanup.finish()
        cleanup.begin()
        cleanup.finish()
        scheduled.first().second()

        assertEquals(2, cancelCount)
        assertFalse(scheduled.isEmpty())
    }
}
