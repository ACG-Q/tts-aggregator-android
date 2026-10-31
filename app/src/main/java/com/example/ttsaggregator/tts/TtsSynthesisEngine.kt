package com.example.ttsaggregator.tts

import com.example.ttsaggregator.data.SynthPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

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
 * 否则系统 TTS 调用方会一直等待。新请求会取消上一请求，避免两条音频同时写回调。
 */
internal class TtsSynthesisEngine(
    private val scope: CoroutineScope,
    private val synthesizeAudio: suspend (String, SynthPrefs) -> Result<ByteArray>,
    private val decode: (ByteArray) -> Mp3Decoder.PcmResult,
) {

    private val lock = Any()
    private var job: Job? = null

    fun synthesize(text: String, prefs: SynthPrefs, sink: SynthesisSink) {
        val newJob = scope.launch {
            if (text.isBlank()) {
                if (sink.start(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS)) sink.done() else sink.error()
                return@launch
            }

            val result = try {
                synthesizeAudio(text, prefs)
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
                sink.error()
                return@launch
            }

            val chunkLimit = sink.maxChunkSize.coerceAtLeast(1)
            var offset = 0
            while (offset < pcm.pcm.size) {
                ensureActive()
                val len = minOf(chunkLimit, pcm.pcm.size - offset)
                if (!sink.write(pcm.pcm, offset, len)) {
                    sink.error()
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
