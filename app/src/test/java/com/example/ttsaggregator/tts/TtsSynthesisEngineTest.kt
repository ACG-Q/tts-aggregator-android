package com.example.ttsaggregator.tts

import com.example.ttsaggregator.data.SynthPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSynthesisEngineTest {

    private fun pcm(size: Int, sampleRate: Int = 24000, channels: Int = 1) =
        Mp3Decoder.PcmResult(ByteArray(size), sampleRate, channels)

    private class RecordingSink(
        private val chunkSize: Int = 3,
        private val startOk: Boolean = true,
        private val writeOk: Boolean = true,
    ) : SynthesisSink {
        val events = mutableListOf<String>()
        var written = 0

        override val maxChunkSize: Int get() = chunkSize

        override fun start(sampleRate: Int, channelCount: Int): Boolean {
            events += "start($sampleRate,$channelCount)"
            return startOk
        }

        override fun write(pcm: ByteArray, offset: Int, length: Int): Boolean {
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
            synthesizeAudio = { _, _ -> synthCalls++; Result.success(byteArrayOf(1)) },
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
            synthesizeAudio = { _, _ -> Result.failure(IllegalStateException("no node")) },
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
            synthesizeAudio = { _, _ -> throw IllegalStateException("boom") },
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
            synthesizeAudio = { _, _ -> Result.success(byteArrayOf(1, 2, 3)) },
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
            synthesizeAudio = { _, _ -> Result.success(byteArrayOf(1)) },
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
            synthesizeAudio = { _, _ -> Result.success(byteArrayOf(1)) },
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
            synthesizeAudio = { _, _ -> Result.success(byteArrayOf(1)) },
            decode = { pcm(9) },
        )
        val sink = RecordingSink(chunkSize = 3, writeOk = false)

        engine.synthesize("你好", SynthPrefs(), sink)
        advanceUntilIdle()

        assertEquals(listOf("start(24000,1)", "write(3)", "error"), sink.events)
    }

    @Test
    fun `新请求取消旧请求，旧回调不再收到事件`() = runTest {
        var calls = 0
        val engine = TtsSynthesisEngine(
            scope = this,
            synthesizeAudio = { _, _ ->
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
            synthesizeAudio = { _, _ -> started = true; awaitCancellation() },
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
