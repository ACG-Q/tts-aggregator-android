package com.example.ttsaggregator.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.example.ttsaggregator.appContainer
import com.example.ttsaggregator.data.SynthPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 系统 TTS 引擎（URL TTS 实现）。
 *
 * 机制：onSynthesizeText 在 Service 工作线程调用。
 * 从聚合节点合成 MP3 → MediaCodec 解码为 PCM → 通过 SynthesisCallback 输出。
 * 合成/解码在独立协程里跑，不阻塞 TTS 工作线程；onStop 取消协程并中断
 * OkHttp 调用，「停止朗读」即时生效。
 */
class AggregatorTtsService : TextToSpeechService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var engine: TtsSynthesisEngine

    override fun onCreate() {
        super.onCreate()
        engine = TtsSynthesisEngine(
            scope = scope,
            synthesizeAudio = { text, prefs -> appContainer.nodePool.synthesize(text, prefs) },
            decode = Mp3Decoder::decode,
        )
    }

    // ---------- 语言相关（云端任意语言，固定返回支持） ----------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        TextToSpeech.LANG_AVAILABLE

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        TextToSpeech.LANG_AVAILABLE

    override fun onGetLanguage(): Array<String> = arrayOf("zh", "CN", "")

    // ---------- 合成 ----------

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return

        val text = request.charSequenceText?.toString().orEmpty()

        // 音色：调用方指定的 Azure 风格 voiceName 优先，否则用首页设置的音色（空 = 节点默认）
        val settings = appContainer.settings.synthSettings.value
        // 框架的 speechRate/pitch 以 100 为基准（int 百分比），转成服务端 -100..100
        val prefs = SynthPrefs(
            voice = request.voiceName?.takeIf { it.contains('-') } ?: settings.voice,
            rate = ((request.speechRate - 100) * 2).coerceIn(-100, 100),
            pitch = ((request.pitch - 100) * 2).coerceIn(-100, 100),
            style = settings.style,
        )

        engine.synthesize(text, prefs, CallbackSink(callback))
    }

    override fun onStop() {
        // 取消合成协程：OkHttp Call 随协程取消，HTTP 下载即时中断
        engine.stop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 把框架的 SynthesisCallback 适配成可单测的 [SynthesisSink] */
    private class CallbackSink(private val callback: SynthesisCallback) : SynthesisSink {
        override val maxChunkSize: Int get() = callback.maxBufferSize.coerceAtLeast(1)

        override fun start(sampleRate: Int, channelCount: Int): Boolean =
            callback.start(
                sampleRate,
                AudioFormat.ENCODING_PCM_16BIT,
                channelCount,
            ) != TextToSpeech.ERROR

        override fun write(pcm: ByteArray, offset: Int, length: Int): Boolean =
            callback.audioAvailable(pcm, offset, length) == TextToSpeech.SUCCESS

        override fun done() {
            callback.done()
        }

        override fun error() {
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        }
    }
}
