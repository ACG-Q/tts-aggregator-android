package com.pulse.tts.tts

import com.pulse.tts.data.CallMode
import com.pulse.tts.data.SynthesisEvent
import com.pulse.tts.data.TtsNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemReadingSessionTest {

    private fun TestScope.core() =
        SystemReadingSessionCore(this) { testScheduler.currentTime }

    @Test
    fun `start enters reading`() = runTest {
        val core = core()
        core.onRequestStarted("你好")

        assertEquals(SystemReadingPhase.READING, core.snapshot.value?.phase)
        assertEquals("你好", core.snapshot.value?.text)
    }

    @Test
    fun `success completes after quiet window`() = runTest {
        val core = core()
        val id = core.onRequestStarted("你好")
        core.onTerminal(id, true)

        advanceTimeBy(1_499)
        runCurrent()
        assertEquals(SystemReadingPhase.READING, core.snapshot.value?.phase)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(SystemReadingPhase.COMPLETED, core.snapshot.value?.phase)
    }

    @Test
    fun `new request within window keeps reading`() = runTest {
        val core = core()
        val first = core.onRequestStarted("第一段")
        val firstSession = core.snapshot.value!!.sessionId
        core.onTerminal(first, true)

        advanceTimeBy(1_400)
        runCurrent()
        core.onRequestStarted("第二段")
        advanceTimeBy(2_000)
        runCurrent()

        val snapshot = core.snapshot.value!!
        assertEquals(SystemReadingPhase.READING, snapshot.phase)
        assertEquals("第二段", snapshot.text)
        assertEquals(firstSession, snapshot.sessionId)
    }

    @Test
    fun `failure ends immediately with error`() = runTest {
        val core = core()
        val id = core.onRequestStarted("你好")
        core.onEvent(id, SynthesisEvent.Failed("节点全部失败", CallMode.AUTO))
        core.onTerminal(id, false)

        assertEquals(SystemReadingPhase.FAILED, core.snapshot.value?.phase)
        assertEquals("节点全部失败", core.snapshot.value?.error)
    }

    @Test
    fun `stop invalidates late terminal callback`() = runTest {
        val core = core()
        val id = core.onRequestStarted("你好")
        core.onStopped()
        core.onTerminal(id, true)
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(SystemReadingPhase.STOPPED, core.snapshot.value?.phase)
    }

    @Test
    fun `event updates node and attempt`() = runTest {
        val core = core()
        val id = core.onRequestStarted("你好")
        core.onEvent(
            id,
            SynthesisEvent.AttemptStarted(
                node = TtsNode(id = "1", baseUrl = "https://a.example", label = "节点 A"),
                attempt = 2,
                total = 3,
                mode = CallMode.AUTO,
            ),
        )

        val snapshot = core.snapshot.value!!
        assertEquals("节点 A", snapshot.nodeLabel)
        assertEquals(2, snapshot.attempt)
        assertEquals(3, snapshot.total)
        assertEquals(CallMode.AUTO, snapshot.mode)
    }

    @Test
    fun `request after completion starts new session`() = runTest {
        val core = core()
        val first = core.onRequestStarted("第一段")
        val firstSession = core.snapshot.value!!.sessionId
        core.onTerminal(first, true)
        advanceTimeBy(1_500)
        runCurrent()
        advanceTimeBy(1_600)
        runCurrent()

        core.onRequestStarted("新的一篇")
        val snapshot = core.snapshot.value!!
        assertNotEquals(firstSession, snapshot.sessionId)
        assertNull(snapshot.error)
        assertTrue(snapshot.attempt >= 0)
    }
}
