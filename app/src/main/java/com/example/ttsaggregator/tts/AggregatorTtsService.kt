package com.example.ttsaggregator.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import com.example.ttsaggregator.appContainer
import com.example.ttsaggregator.data.SynthPrefs
import com.example.ttsaggregator.data.TtsNode

/**
 * 系统 TTS 引擎（URL TTS 实现）。
 *
 * 机制：onSynthesizeText 在 Service 工作线程调用。
 * 从聚合节点合成 MP3 → MediaCodec 解码为 PCM → 通过 SynthesisCallback 输出。
 * 音频由调用方播放，因此不需要 ExoPlayer / MediaPlayer / 前台通知。
 */
class AggregatorTtsService : TextToSpeechService() {

    private val tag = "AggregatorTtsService"

    @Volatile
    private var stopped = false

    // ---------- 语言相关（云端任意语言，固定返回支持） ----------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        TextToSpeech.LANG_AVAILABLE

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        TextToSpeech.LANG_AVAILABLE

    override fun onGetLanguage(): Array<String> = arrayOf("zh", "CN", "")

    // ---------- 合成 ----------

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        stopped = false

        val text = request.charSequenceText?.toString().orEmpty()
        if (text.isBlank()) {
            callback.done()
            return
        }

        // 音色：调用方指定的 Azure 风格 voiceName 优先，否则用首页设置的音色（空 = 节点默认）
        val settings = appContainer.settings.synthSettings.value
        // 框架的 speechRate/pitch 以 100 为基准（int 百分比），转成服务端 -100..100
        val prefs = SynthPrefs(
            voice = request.voiceName?.takeIf { it.contains('-') } ?: settings.voice,
            rate = ((request.speechRate - 100) * 2).coerceIn(-100, 100),
            pitch = ((request.pitch - 100) * 2).coerceIn(-100, 100),
        )

        // 1) 通过节点池合成 MP3（自动/组/手选三种调度逻辑）
        val mp3 = runCatching {
            kotlinx.coroutines.runBlocking {
                appContainer.nodePool.synthesize(text, prefs)
            }
        }.getOrElse { Result.failure(it) }.getOrElse { e ->
            Log.e(tag, "synthesis failed: ${e.message}")
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }

        // 2) 解码为 PCM（callback 只接受 PCM）
        val pcm = try {
            Mp3Decoder.decode(mp3)
        } catch (e: Exception) {
            Log.e(tag, "decode failed: ${e.message}")
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }

        // 3) 声明音频格式并分块输出
        val startResult = callback.start(
            pcm.sampleRate,
            AudioFormat.ENCODING_PCM_16BIT,
            pcm.channelCount,
        )
        if (startResult == TextToSpeech.ERROR) {
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }

        val chunk = callback.maxBufferSize
        var offset = 0
        while (offset < pcm.pcm.size && !stopped) {
            val len = minOf(chunk, pcm.pcm.size - offset)
            if (callback.audioAvailable(pcm.pcm, offset, len) != TextToSpeech.SUCCESS) break
            offset += len
        }
        if (!stopped) callback.done() else callback.error(TextToSpeech.ERROR_SYNTHESIS)
    }

    override fun onStop() {
        stopped = true
    }
}
