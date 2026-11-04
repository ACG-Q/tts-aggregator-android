package com.pulse.tts.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TTS 节点 HTTP 客户端：探测 / 元数据 / 音色 / 合成。
 *
 * 纯网络层，不碰存储。所有方法挂起、失败返回 null 或抛异常，
 * 由上层（NodeRepository / NodePool）决定重试、落库与用户提示。
 *
 * 兼容两代 zuoban/tts 部署：
 * - 新版 V1：/api/v1/{health,voices,tts}
 * - 老版：/tts、/voices（部分部署 /voices 挂起 → 短超时）
 */
class TtsApiClient {

    // 合成用：允许较长等待（长文本服务端处理慢）
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // 探测/元数据用：短超时（老版 /voices 部署实测挂起，不能拖死探测）
    private val shortHttp = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---------- 探测 ----------

    /**
     * 全量探测一个节点：免Key判定 + 元数据。
     * 返回带 health/lastChecked/音色数/API 世代的节点（保留入参 id/label/apiKey）。
     */
    suspend fun probe(base: String, label: String = "", apiKey: String? = null): TtsNode =
        withContext(Dispatchers.IO) {
            val url = base.removeTrailingSlash()
            val keyless = probeKeyless(url, apiKey)
            val meta = fetchMeta(url, apiKey)
            Log.d(TAG, "probe: $url keyless=$keyless meta=${meta != null} ver=${meta?.version}")
            TtsNode(
                baseUrl = url,
                label = label,
                keyless = keyless && apiKey.isNullOrBlank(),
                apiKey = apiKey,
                health = if (meta != null || keyless) TtsNode.Health.OK else TtsNode.Health.FAIL,
                lastChecked = System.currentTimeMillis(),
                defaultVoice = meta?.defaultVoice ?: DEFAULT_VOICE,
                voiceCount = meta?.voiceCount ?: 0,
                apiVersion = meta?.version ?: ApiVersion.UNKNOWN,
            )
        }

    /** 免 Key 判定：GET {base}/tts → 400 + "必须提供文本参数" ⇒ 免 Key 可用（该端点秒回，用短超时） */
    private suspend fun probeKeyless(base: String, apiKey: String?): Boolean {
        val url = authUrl("$base/tts", apiKey) ?: return false
        return retry {
            val resp = shortHttp.newCall(Request.Builder().url(url).get().build()).await()
            resp.use {
                it.code == 400 && (it.body?.string()?.contains("必须提供文本参数") == true)
            }
        } ?: false
    }

    /**
     * 元数据（新老版本回退，健康度优先）：
     * 1. health 快速判版本（新老部署都秒回，老版 404）
     * 2. 新版：/api/v1/voices 拿音色数
     * 3. 老版：/voices 拿音色数（部分部署该端点极慢 → 短超时，失败不影响健康判定，
     *    因为 keyless（/tts 可达）已足够证明老版节点可用）
     */
    private suspend fun fetchMeta(base: String, apiKey: String?): NodeMeta? {
        val healthUrl = authUrl("$base/api/v1/health", apiKey) ?: return null
        val isV1 = retry {
            shortHttp.newCall(Request.Builder().url(healthUrl).get().build()).await()
                .use { it.isSuccessful }
        } ?: return null // 连 health 都不通 → 无法判定

        if (isV1) {
            // 新版：拉音色数（失败也判 V1 可用）
            val voicesUrl = authUrl("$base/api/v1/voices", apiKey) ?: return NodeMeta(0, DEFAULT_VOICE, ApiVersion.V1)
            val meta = retry {
                shortHttp.newCall(Request.Builder().url(voicesUrl).get().build()).await()
                    .use {
                        if (!it.isSuccessful) return@retry null
                        NodeMeta(AppJson.parseToJsonElement(it.body!!.string()).jsonArray.size, DEFAULT_VOICE, ApiVersion.V1)
                    }
            }
            return meta ?: NodeMeta(0, DEFAULT_VOICE, ApiVersion.V1)
        }

        // 老版：/voices 部分部署极慢（实测挂起），短超时拉取，失败不影响健康
        val legacyVoicesUrl = authUrl("$base/voices", apiKey) ?: return NodeMeta(0, DEFAULT_VOICE, ApiVersion.LEGACY)
        val meta = retry(times = 2) {
            shortHttp.newCall(Request.Builder().url(legacyVoicesUrl).get().build()).await()
                .use {
                    if (!it.isSuccessful) return@retry null
                    NodeMeta(AppJson.parseToJsonElement(it.body!!.string()).jsonArray.size, DEFAULT_VOICE, ApiVersion.LEGACY)
                }
        }
        return meta ?: NodeMeta(0, DEFAULT_VOICE, ApiVersion.LEGACY)
    }

    data class NodeMeta(val voiceCount: Int, val defaultVoice: String, val version: ApiVersion)

    // ---------- 音色 ----------

    /**
     * 拉取音色列表：新老端点回退，自动附带 node 的 API Key。
     * @return null = 两个端点都失败
     */
    suspend fun fetchVoices(node: TtsNode): List<Voice>? {
        val base = node.baseUrl.removeTrailingSlash()
        // 新老版本端点回退：/api/v1/voices（新版） → /voices（老版，部分部署该端点挂起 → 短超时）
        val urls = listOf("$base/api/v1/voices" to http, "$base/voices" to shortHttp)
        for ((endpoint, client) in urls) {
            val signed = endpoint.withApiKeyIfNeeded(node) ?: continue
            val list = retry {
                client.newCall(Request.Builder().url(signed).get().build()).await().use {
                    if (it.isSuccessful) AppJson.decodeFromString(ListSerializer(Voice.serializer()), it.body!!.string())
                    else null
                }
            }
            if (!list.isNullOrEmpty()) return list
        }
        return null
    }

    // ---------- 合成 ----------

    /**
     * 合成文本 → 音频字节。三级端点回退，覆盖新老版本部署：
     * 1. POST /api/v1/tts   —— 新版 v1（wanjq 等）
     * 2. POST /tts          —— 老版（66421ee 等，JSON 字段相同）
     * 3. GET  /tts?t=&text= —— 兼容层（新老都认，参数双发）
     *
     * @return null = 三级端点均失败（调用方决定是否重试/报错）
     */
    suspend fun synthesize(node: TtsNode, text: String, prefs: SynthPrefs, retries: Int = SYNTH_RETRIES): ByteArray? {
        val result = retry(times = retries) {
            postSynthesize(node, text, prefs, node.v1TtsEndpoint)
                ?: postSynthesize(node, text, prefs, node.ttsEndpoint)
                ?: getSynthesize(node, text, prefs)
        }
        if (result == null) Log.w(TAG, "synthesize failed: ${node.baseUrl}")
        return result
    }

    private suspend fun postSynthesize(
        node: TtsNode,
        text: String,
        p: SynthPrefs,
        endpoint: String,
    ): ByteArray? =
        runCatching {
            val json = buildJsonObject {
                put("text", text)
                if (p.voice.isNotBlank()) put("voice", p.voice)
                else if (node.defaultVoice.isNotBlank()) put("voice", node.defaultVoice)
                put("rate", p.rate)
                put("pitch", p.pitch)
                if (p.style.isNotBlank()) put("style", p.style)
            }.toString().toRequestBody(jsonType)
            val url = endpoint.withApiKeyIfNeeded(node) ?: return null
            val resp = http.newCall(Request.Builder().url(url).post(json).build()).await()
            // 200 + 空 body 是失败：空音频会让解码器空转
            resp.use { if (it.isSuccessful) it.body?.bytes()?.takeIf { b -> b.isNotEmpty() } else null }
        }.getOrNull()

    /**
     * 兼容 GET 回退：不同部署参数名不一致
     * （有的只认旧参数 t=，有的只认 text=，同时发送两者覆盖全部版本）
     */
    private suspend fun getSynthesize(node: TtsNode, text: String, p: SynthPrefs): ByteArray? =
        runCatching {
            val url = node.ttsEndpoint.toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("text", text)
                ?.addQueryParameter("t", text)
                ?.addQueryParameter("v", p.voice.ifBlank { node.defaultVoice })
                ?.addQueryParameter("r", p.rate.toString())
                ?.addQueryParameter("p", p.pitch.toString())
                ?.apply { if (p.style.isNotBlank()) addQueryParameter("s", p.style) }
                ?.apply {
                    if (!node.keyless && !node.apiKey.isNullOrBlank()) addQueryParameter("api_key", node.apiKey)
                }
                ?.build() ?: return null
            val resp = http.newCall(Request.Builder().url(url).get().build()).await()
            resp.use { if (it.isSuccessful) it.body?.bytes()?.takeIf { b -> b.isNotEmpty() } else null }
        }.getOrNull()

    // ---------- 工具 ----------

    /** 老版认证只认 query/header（JSON 里没有 api_key 字段），统一挂 query */
    private fun String.withApiKeyIfNeeded(node: TtsNode): okhttp3.HttpUrl? =
        toHttpUrlOrNull()?.newBuilder()?.apply {
            if (!node.keyless && !node.apiKey.isNullOrBlank()) addQueryParameter("api_key", node.apiKey)
        }?.build()

    private suspend fun <T> retry(times: Int = 3, block: suspend () -> T?): T? {
        repeat(times - 1) { attempt ->
            val r = runCatching { block() }.getOrNull()
            if (r != null) return r
            Log.w(TAG, "retry ${attempt + 1}/$times failed, retrying…")
            kotlinx.coroutines.delay(400L * (attempt + 1))
        }
        return runCatching { block() }.getOrNull()
    }

    companion object {
        private const val TAG = "TTS-Agg"
        const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"
        private const val SYNTH_RETRIES = 3
    }
}

/** 解析端点并仅在提供 Key 时附加认证参数（供探测等无 keyless 判定的调用使用）。 */
internal fun authUrl(url: String, apiKey: String?): okhttp3.HttpUrl? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    if (apiKey.isNullOrBlank()) return parsed
    return parsed.newBuilder().addQueryParameter("api_key", apiKey).build()
}
