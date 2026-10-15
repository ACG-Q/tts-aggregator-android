package com.example.ttsaggregator.localserver

import com.example.ttsaggregator.data.SynthPrefs
import com.example.ttsaggregator.data.SynthSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** /tts 请求的处理结果：成功给 MP3 字节，失败给 HTTP 状态码 + 中文信息 */
sealed interface TtsResult {
    data class Audio(val bytes: ByteArray) : TtsResult
    data class Error(val code: Int, val message: String) : TtsResult
}

/** POST /tts 的 JSON body（多余字段忽略，缺省项回退应用默认） */
@Serializable
private data class PostBody(
    val text: String? = null,
    val voice: String? = null,
    val rate: Int? = null,
    val pitch: Int? = null,
    val style: String? = null,
)

/**
 * /tts 端点纯逻辑：HTTP 参数 → [SynthPrefs]，合成结果 → [TtsResult]。
 * 不依赖 Android 与 HTTP 框架，JVM 单测可直接构造。
 */
class LocalTtsRequestHandler(
    private val defaults: () -> SynthSettings,
    private val synthesize: (String, SynthPrefs) -> Result<ByteArray>,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** GET /tts?t=<文本>[&text=][&v=][&r=][&p=][&s=]，t/text 双发兼容节点协议 */
    fun handleGet(params: Map<String, List<String>>): TtsResult {
        val text = params["t"]?.firstOrNull() ?: params["text"]?.firstOrNull()
            ?: return TtsResult.Error(400, "缺少 text 参数")
        if (text.isBlank()) return TtsResult.Error(400, "文本不能为空")
        val d = defaults()
        val prefs = SynthPrefs(
            voice = params["v"]?.firstOrNull() ?: d.voice,
            rate = params["r"]?.firstOrNull()?.toIntOrNull()?.coerceIn(-100, 100) ?: d.rate,
            pitch = params["p"]?.firstOrNull()?.toIntOrNull()?.coerceIn(-100, 100) ?: d.pitch,
            style = params["s"]?.firstOrNull() ?: "",
        )
        return finish(synthesize(text, prefs))
    }

    /** POST /tts，application/json：{"text","voice"?,"rate"?,"pitch"?,"style"?} */
    fun handlePost(body: String): TtsResult {
        val parsed = try {
            json.decodeFromString(PostBody.serializer(), body)
        } catch (e: Exception) {
            return TtsResult.Error(400, "请求体不是合法 JSON：${e.message}")
        }
        val text = parsed.text ?: return TtsResult.Error(400, "缺少 text 字段")
        if (text.isBlank()) return TtsResult.Error(400, "文本不能为空")
        val d = defaults()
        val prefs = SynthPrefs(
            voice = parsed.voice ?: d.voice,
            rate = parsed.rate?.coerceIn(-100, 100) ?: d.rate,
            pitch = parsed.pitch?.coerceIn(-100, 100) ?: d.pitch,
            style = parsed.style ?: "",
        )
        return finish(synthesize(text, prefs))
    }

    private fun finish(result: Result<ByteArray>): TtsResult = result.fold(
        onSuccess = { TtsResult.Audio(it) },
        onFailure = { TtsResult.Error(502, "合成失败：${it.message ?: "未知错误"}") },
    )
}
