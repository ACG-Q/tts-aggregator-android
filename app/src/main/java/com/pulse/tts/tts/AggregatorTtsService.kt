package com.pulse.tts.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
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
 * 机制：onSynthesizeText 在框架的合成线程调用，必须等本段写完再返回，
 * 否则框架会立刻投递下一段，上一段仍在写入的音频会被取消。
 * 从聚合节点合成 MP3 → MediaCodec 解码为 PCM → 通过 SynthesisCallback 输出。
 * 合成/解码在协程里跑，调用线程只做等待；onStop 取消协程并中断 OkHttp 调用，
 * 「停止朗读」即时生效。
 *
 * 语言/音色元数据只暴露一个中文音色：默认实现会枚举全部 locale 生成几百个
 * 虚假 voice，并让默认语言与引擎实际音色不一致。
 */
class AggregatorTtsService : TextToSpeechService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var engine: TtsSynthesisEngine
    private lateinit var notifier: SynthesisNotification

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        notifier = SynthesisNotification(this)
        engine = TtsSynthesisEngine(
            scope = scope,
            synthesizeAudio = { text, prefs, onEvent ->
                appContainer.nodePool.synthesize(text, prefs, onEvent)
            },
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

        startForegroundSafely(notifier.start(text))
        // 必须等本次合成写完再返回：框架在 onSynthesizeText 返回后立即投递下一段，
        // 提前返回会让下一段取消当前段，最终只剩最后一段有声音。
        try {
            engine.synthesizeNow(
                text,
                prefs,
                CallbackSink(callback),
                notifier::handleEvent,
            )
        } finally {
            endSynthesis()
        }
    }

    override fun onStop() {
        // 取消合成协程：OkHttp Call 随协程取消，HTTP 下载即时中断
        engine.stop()
        endSynthesis()
    }

    override fun onDestroy() {
        isRunning = false
        endSynthesis()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 所有结束路径的统一出口：先退出前台，再取消通知。
     * NotificationCleanup 负责 350ms 与 2s 的兜底取消。
     */
    private fun endSynthesis() {
        stopForegroundSafely()
        notifier.finish()
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        try {
            startForeground(SynthesisNotification.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限：退化为普通通知，不影响合成
            Log.w(TAG, "startForeground failed, fallback to notification", e)
            notifier.post(notification)
        }
    }

    private fun stopForegroundSafely() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    /** 把框架的 SynthesisCallback 适配成可单测的 [SynthesisSink] */
    private class CallbackSink(
        private val callback: SynthesisCallback,
    ) : SynthesisSink {
        override val maxChunkSize: Int get() = callback.maxBufferSize.coerceAtLeast(1)

        override fun start(sampleRate: Int, channelCount: Int): Boolean =
            callback.start(
                sampleRate,
                AudioFormat.ENCODING_PCM_16BIT,
                channelCount,
            ) == TextToSpeech.SUCCESS

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
        private const val TAG = "TTS-Agg"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
