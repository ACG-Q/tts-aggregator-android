package com.pulse.tts.tts

import com.pulse.tts.data.SynthPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSynthesisEngineTest {

    private fun pcm(size: Int, sampleRate: Int = 24000, channels: Int = 1) =
        Mp3Decoder.PcmResult(ByteArray(size), sampleRate, channels)

    private class RecordingSink(
        private val chunkSize: Int = 3,
        private val startOk: Boolean = true,
        private val writeOk: Boolean = true,
        private val onStart: (() -> Unit)? = null,
        private val onWrite: (() -> Unit)? = null,
    ) : SynthesisSink {
        val events = mutableListOf<String>()
        var written = 0

        override val maxChunkSize: Int get() = chunkSize

        override fun start(sampleRate: Int, channelCount: Int): Boolean {
            onStart?.invoke()
            events += "start($sampleRate,$channelCount)"
            return startOk
        }

        override fun write(pcm: ByteArray, offset: Int, length: Int): Boolean {
            onWrite?.invoke()
            events += "write($length)"
            if (writeOk) written += length
            return writeOk
        }

        override fun done() {
            events += "done"
        }

        override fun error() {
            events += "error"
        }
    }

    @Test
    fun `空文本先 start 再 done，不合成不解码`() = runTest {
        var synthCalls = 0
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> synthCalls++; Result.success(byteArrayOf(1)) },
            decode = { error("空文本不应解码") },
        )
        val sink = RecordingSink()

        engine.synthesize("   ", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "done"), sink.events)
        assertEquals(0, synthCalls)
    }

    @Test
    fun `合成失败调用 error，不 start`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> Result.failure(IllegalStateException("no node")) },
            decode = { error("失败时不应解码") },
        )
        val sink = RecordingSink()

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("error"), sink.events)
    }

    @Test
    fun `合成抛异常调用 error，不崩溃`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> throw IllegalStateException("boom") },
            decode = { error("失败时不应解码") },
        )
        val sink = RecordingSink()

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("error"), sink.events)
    }

    @Test
    fun `解码失败调用 error，不 start`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> Result.success(byteArrayOf(1, 2, 3)) },
            decode = { throw IllegalStateException("bad mp3") },
        )
        val sink = RecordingSink()

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("error"), sink.events)
    }

    @Test
    fun `start 失败调用 error`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> Result.success(byteArrayOf(1)) },
            decode = { pcm(4) },
        )
        val sink = RecordingSink(startOk = false)

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "error"), sink.events)
    }

    @Test
    fun `成功按 maxChunkSize 分块写入后 done`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> Result.success(byteArrayOf(1)) },
            decode = { pcm(7) },
        )
        val sink = RecordingSink(chunkSize = 3)

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "write(3)", "write(3)", "write(1)", "done"), sink.events)
        assertEquals(7, sink.written)
    }

    @Test
    fun `写入失败调用 error，不再 done`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> Result.success(byteArrayOf(1)) },
            decode = { pcm(9) },
        )
        val sink = RecordingSink(chunkSize = 3, writeOk = false)

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "write(3)", "error"), sink.events)
    }

    @Test
    fun `synthesize 返回的 Job 在回调完成后才结束，供框架按段串行等待`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> Result.success(byteArrayOf(1)) },
            decode = { pcm(3) },
        )
        val sink = RecordingSink()

        val job = engine.synthesize("你好", SynthPrefs(), sink)
        assertFalse("合成未结束时 Job 不应完成", job.isCompleted)

        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(listOf("start(24000,1)", "write(3)", "done"), sink.events)
    }

    @Test
    fun `停止朗读导致的写入失败不当作错误上报`() = runTest {
        var engineRef: TtsSynthesisEngine? = null
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> Result.success(byteArrayOf(1)) },
            decode = { pcm(300) },
        )
        engineRef = engine
        val sink = RecordingSink(
            chunkSize = 10,
            writeOk = false,
            onWrite = { engineRef?.stop() },
        )

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "write(10)"), sink.events)
    }

    @Test
    fun `synthesizeNow 返回时本段已写完回调`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val engine = TtsSynthesisEngine(
                scope = scope,
                synthesizeAudio = { _, _, _ -> Result.success(byteArrayOf(1)) },
                decode = { pcm(7) },
            )
            val sink = RecordingSink(chunkSize = 3)

            engine.synthesizeNow("你好", SynthPrefs(), sink)

            assertEquals(
                listOf("start(24000,1)", "write(3)", "write(3)", "write(1)", "done"),
                sink.events,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `synthesizeNow 在 stop 后解除阻塞，不卡死框架线程`() {
        val scope = CoroutineScope(Dispatchers.Default)
        val started = CountDownLatch(1)
        val returned = CountDownLatch(1)
        try {
            val engine = TtsSynthesisEngine(
                scope = scope,
                synthesizeAudio = { _, _, _ ->
                    started.countDown()
                    awaitCancellation()
                },
                decode = { pcm(3) },
            )
            val sink = RecordingSink()
            val worker = thread(name = "synthesis-thread") {
                engine.synthesizeNow("长文本", SynthPrefs(), sink)
                returned.countDown()
            }

            assertTrue("合成应先开始", started.await(2, TimeUnit.SECONDS))
            engine.stop()

            assertTrue(
                "stop 后 synthesizeNow 必须返回，否则框架线程会永久阻塞",
                returned.await(2, TimeUnit.SECONDS),
            )
            worker.join(2_000)
            assertEquals(emptyList<String>(), sink.events)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `stop 之后的新请求仍能完整合成`() = runTest {
        var calls = 0
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ ->
                if (calls++ == 0) awaitCancellation() else Result.success(byteArrayOf(1))
            },
            decode = { pcm(3) },
        )
        val first = RecordingSink()
        val second = RecordingSink()

        engine.synthesize("第一段", SynthPrefs(), first)
        runCurrent()
        engine.stop()
        advanceUntilIdle()

        engine.synthesize("第二段", SynthPrefs(), second)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "write(3)", "done"), second.events)
    }

    @Test
    fun `两段依次等待时都完整写完`() = runTest {
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { text, _, _ -> Result.success(text.toByteArray()) },
            decode = { pcm(3) },
        )
        val first = RecordingSink()
        val second = RecordingSink()

        engine.synthesize("第一段", SynthPrefs(), first)
        advanceUntilIdle()
        engine.synthesize("第二段", SynthPrefs(), second)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "write(3)", "done"), first.events)
        assertEquals(listOf("start(24000,1)", "write(3)", "done"), second.events)
    }

    @Test
    fun `新请求取消旧请求，旧回调不再收到事件`() = runTest {
        var calls = 0
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ ->
                if (calls++ == 0) awaitCancellation()
                Result.success(byteArrayOf(1))
            },
            decode = { pcm(3) },
        )
        val first = RecordingSink()
        val second = RecordingSink()

        engine.synthesize("第一句", SynthPrefs(), first)
        runCurrent()
        engine.synthesize("第二句", SynthPrefs(), second)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), first.events)
        assertEquals(listOf("start(24000,1)", "write(3)", "done"), second.events)
    }

    @Test
    fun `stop 取消进行中的合成，不写任何回调`() = runTest {
        var started = false
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _, _ -> started = true; awaitCancellation() },
            decode = { pcm(3) },
        )
        val sink = RecordingSink()

        engine.synthesize("长文本", SynthPrefs(), sink)
        runCurrent()
        assertTrue("合成应先开始", started)
        engine.stop()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), sink.events)
    }
}
