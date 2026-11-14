package com.pulse.tts.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import com.pulse.tts.appContainer
import com.pulse.tts.data.SynthPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.Locale

/**
 * 系统 TTS 引擎（URL TTS 实现）。
 *
 * 机制：onSynthesizeText 在 Service 工作线程调用。
 * 从聚合节点合成 MP3 → MediaCodec 解码为 PCM → 通过 SynthesisCallback 输出。
 * 合成/解码在独立协程里跑，不阻塞 TTS 工作线程；onStop 取消协程并中断
 * OkHttp 调用，「停止朗读」即时生效。
 *
 * 语言/音色元数据只暴露一个中文音色：默认实现会枚举全部 locale 生成几百个
 * 虚假 voice，并让默认语言与引擎实际音色不一致。
 */
class AggregatorTtsService : TextToSpeechService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var engine: TtsSynthesisEngine

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        engine = TtsSynthesisEngine(
            scope = scope,
            synthesizeAudio = { text, prefs -> appContainer.nodePool.synthesize(text, prefs) },
            decode = Mp3Decoder::decode,
        )
    }

    // ---------- 语言/音色元数据（云端任意语言，固定一个中文音色） ----------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        TtsLanguageSupport.availabilityOf(lang, country, variant)

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        TtsLanguageSupport.availabilityOf(lang, country, variant)

    override fun onGetLanguage(): Array<String> = arrayOf("zho", "CHN", "")

    override fun onGetVoices(): List<Voice> = listOf(defaultVoice())

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String =
        TtsLanguageSupport.VOICE_NAME

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (voiceName == TtsLanguageSupport.VOICE_NAME) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    @Suppress("DEPRECATION") // 系统设置的「需要网络」判断仍读取该 feature key
    override fun onGetFeaturesForLanguage(
        lang: String?,
        country: String?,
        variant: String?,
    ): Set<String> = setOf(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)

    @Suppress("DEPRECATION")
    private fun defaultVoice(): Voice = Voice(
        TtsLanguageSupport.VOICE_NAME,
        Locale.SIMPLIFIED_CHINESE,
        Voice.QUALITY_NORMAL,
        Voice.LATENCY_NORMAL,
        true,
        setOf(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS),
    )

    // ---------- 合成 ----------

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return

        val text = request.charSequenceText?.toString().orEmpty()
        // 音色：引擎只对外声明一个音色，实际音色统一用首页设置（空 = 节点默认）
        val settings = appContainer.settings.synthSettings.value
        // 框架的 speechRate/pitch 以 100 为基准（int 百分比），转成服务端 -100..100
        val prefs = SynthPrefs(
            voice = settings.voice,
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
        isRunning = false
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

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
