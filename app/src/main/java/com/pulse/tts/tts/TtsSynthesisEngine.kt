package com.pulse.tts.tts

import com.pulse.tts.data.SynthesisEvent
import com.pulse.tts.data.SynthPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 合成回调抽象：隔离 Android [android.speech.tts.SynthesisCallback]，便于 JVM 单测。
 */
internal interface SynthesisSink {
    /** 回调可接受的单次最大 PCM 字节数 */
    val maxChunkSize: Int

    /** @return false = 框架拒绝启动，本次合成终止 */
    fun start(sampleRate: Int, channelCount: Int): Boolean

    /** @return false = 下游写入失败，本次合成终止 */
    fun write(pcm: ByteArray, offset: Int, length: Int): Boolean

    fun done()

    fun error()
}

/**
 * 系统 TTS 合成流水线：合成 → 解码 → 分块回调。
 *
 * 所有终止路径（成功 / 异常 / 取消）都必须给 [SynthesisSink] 一个明确结局，
 * 否则系统 TTS 调用方会一直等待。
 *
 * 返回的 [Job] 代表本次合成从入队到写完回调的全过程。`TextToSpeechService`
 * 要求 `onSynthesizeText` 在本次合成结束前不返回，否则框架会立刻投递下一段，
 * 上一段仍在写的音频会被新请求打断（表现为「只读最后一段」）。调用方应等待
 * 该 Job 完成后再返回。
 */
internal class TtsSynthesisEngine(
    private val scope: CoroutineScope,
    private val synthesizeAudio: suspend (
        text: String,
        prefs: SynthPrefs,
        onEvent: (SynthesisEvent) -> Unit,
    ) -> Result<ByteArray>,
    private val decode: (ByteArray) -> Mp3Decoder.PcmResult,
) {

    private val lock = Any()
    private var job: Job? = null

    fun synthesize(
        text: String,
        prefs: SynthPrefs,
        sink: SynthesisSink,
        onEvent: (SynthesisEvent) -> Unit = {},
    ): Job {
        // 先拿到 Job 再启动协程：避免协程已经跑起来、job 还没赋值的窗口，
        // 使得 stop() 在任何时刻都能取消当前请求。
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            if (text.isBlank()) {
                if (sink.start(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS)) sink.done() else sink.error()
                return@launch
            }

            val result = try {
                synthesizeAudio(text, prefs, onEvent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sink.error()
                return@launch
            }
            val bytes = result.getOrNull()
            if (bytes == null) {
                sink.error()
                return@launch
            }

            val pcm = try {
                decode(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sink.error()
                return@launch
            }

            ensureActive()
            if (!sink.start(pcm.sampleRate, pcm.channelCount)) {
                // 停止朗读会让框架拒绝 start；此时不是合成故障，不再上报 error
                if (isActive) sink.error()
                return@launch
            }

            val chunkLimit = sink.maxChunkSize.coerceAtLeast(1)
            var offset = 0
            while (offset < pcm.pcm.size) {
                ensureActive()
                val len = minOf(chunkLimit, pcm.pcm.size - offset)
                if (!sink.write(pcm.pcm, offset, len)) {
                    // 同上：取消导致的写入失败不当作错误
                    if (isActive) sink.error()
                    return@launch
                }
                offset += len
            }
            sink.done()
        }

        synchronized(lock) {
            job?.cancel()
            job = newJob
        }
        newJob.start()
        return newJob
    }

    /**
     * 阻塞直到本段合成写完回调，供 `TextToSpeechService` 的合成线程调用。
     *
     * 框架要求 `onSynthesizeText` 在本次合成结束前不返回；提前返回会让框架
     * 立即投递下一段，新请求取消上一段，表现为「只读最后一段」。
     * `stop()` 会取消当前 Job，因此这里不会永久阻塞。
     */
    fun synthesizeNow(
        text: String,
        prefs: SynthPrefs,
        sink: SynthesisSink,
        onEvent: (SynthesisEvent) -> Unit = {},
    ) {
        runBlocking { synthesize(text, prefs, sink, onEvent).join() }
    }

    fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 24000
        const val DEFAULT_CHANNELS = 1
    }
}
