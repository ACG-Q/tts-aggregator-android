package com.example.ttsaggregator.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 音色仓库：内置资产 + 缓存 + 网络三层数据源。
 *
 * - 内置：[ASSET_NAME] 随 APK 打包 958 条声音，带中文名与中文简介
 * - 缓存：SharedPreferences，保存上次成功拉取并合并翻译的列表
 * - 网络：仅在用户点“更新”时强制刷新，失败继续沿用内置/缓存
 *
 * 网络返回的声音缺少 `name_zh` / `intro_zh` 时，按 short_name 合并内置翻译；
 * 仍找不到时回退到原始显示名，由界面生成简介兜底。
 */
class VoiceRepository(
    context: Context,
    private val settings: SettingsStore,
    private val api: TtsApiClient,
    private val nodes: () -> List<TtsNode>, // 惰性取节点：避免构造期环依赖
) {

    private val appContext = context.applicationContext

    private val builtin: List<Voice> = loadBuiltin()

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /** 原子互斥：防止并发协程重复拉取（读-改-写非原子会漏判） */
    private val loadLock = AtomicBoolean(false)

    val builtinCount: Int get() = builtin.size

    /** 冷启动：缓存优先，缺失时直接使用内置数据 */
    init {
        val cached = decodeCache(settings.readRaw(SettingsStore.KEY_VOICES_CACHE))
        _voices.value = cached?.let { mergeVoiceTranslations(it, builtin) }
            ?: builtin
    }

    /**
     * 加载音色列表。
     * @param source 指定数据来源节点；为空时用第一个健康节点
     * @param force  跳过内存缓存强制重新拉取
     * @return 是否成功应用网络数据
     */
    suspend fun load(source: TtsNode? = null, force: Boolean = false): Boolean {
        if (!force && _voices.value.isNotEmpty()) return true
        if (!loadLock.compareAndSet(false, true)) return false
        _loading.value = true
        try {
            // 指定来源优先；否则按健康节点顺序逐个尝试，避免首个节点异常导致整页无音色
            val candidates = source?.let { listOf(it) }
                ?: nodes().filter { it.health == TtsNode.Health.OK && !it.disabled }
            for (node in candidates) {
                val list = api.fetchVoices(node)
                if (!list.isNullOrEmpty()) {
                    applyRemote(list)
                    return true
                }
            }
            Log.w(TAG, "无法获取音色列表（已尝试 ${candidates.size} 个节点）")
            return false
        } finally {
            loadLock.set(false)
            _loading.value = false
        }
    }

    /** 用户主动“更新”：强制联网，并返回可展示的结果。 */
    suspend fun refresh(): VoiceRefreshResult {
        if (!loadLock.compareAndSet(false, true)) return VoiceRefreshResult.Busy
        _loading.value = true
        return try {
            val candidates = nodes().filter { !it.disabled }
                .sortedBy { if (it.health == TtsNode.Health.OK) 0 else 1 }
            if (candidates.isEmpty()) return VoiceRefreshResult.Failure("没有可用节点")
            // 部分节点 /voices 会长时间挂起，更新操作必须限时返回，失败时保留内置数据
            val found = withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                candidates.firstNotNullOfOrNull { node ->
                    withTimeoutOrNull(NODE_TIMEOUT_MS) {
                        api.fetchVoices(node)?.takeIf { it.isNotEmpty() }
                    }
                }
            }
            if (found != null) {
                applyRemote(found)
                VoiceRefreshResult.Success(found.size)
            } else {
                VoiceRefreshResult.Failure("节点无响应或未返回声音数据")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refresh voices failed", e)
            VoiceRefreshResult.Failure(e.message ?: "网络请求失败")
        } finally {
            loadLock.set(false)
            _loading.value = false
        }
    }

    private fun applyRemote(remote: List<Voice>) {
        val merged = mergeVoiceTranslations(remote, builtin)
        _voices.value = merged
        settings.writeRaw(
            SettingsStore.KEY_VOICES_CACHE,
            AppJson.encodeToString(ListSerializer(Voice.serializer()), merged),
        )
    }

    private fun loadBuiltin(): List<Voice> = runCatching {
        appContext.assets.open(ASSET_NAME)
            .bufferedReader()
            .use { it.readText() }
            .let { AppJson.decodeFromString(ListSerializer(Voice.serializer()), it) }
    }.onFailure {
        Log.e(TAG, "读取内置音色失败", it)
    }.getOrDefault(emptyList())

    private fun decodeCache(raw: String?): List<Voice>? = raw
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { AppJson.decodeFromString(ListSerializer(Voice.serializer()), it) }.getOrNull() }
        ?.takeIf { it.isNotEmpty() }

    companion object {
        private const val TAG = "VoiceRepository"
        private const val ASSET_NAME = "voices.json"
        /** 主动更新的总时长上限，避免慢节点让按钮一直转圈 */
        private const val REFRESH_TIMEOUT_MS = 30_000L
        /** 单个节点的等待上限，超时后换下一个节点 */
        private const val NODE_TIMEOUT_MS = 10_000L
    }
}

/** “更新声音”的结果，供界面提示。 */
sealed interface VoiceRefreshResult {
    data class Success(val count: Int) : VoiceRefreshResult

    data class Failure(val reason: String) : VoiceRefreshResult

    object Busy : VoiceRefreshResult
}

/**
 * 网络声音合并内置中文翻译：
 * 优先按 short_name（id）精确匹配，其次按基础人名匹配，已有翻译优先保留。
 */
internal fun mergeVoiceTranslations(remote: List<Voice>, builtin: List<Voice>): List<Voice> {
    if (builtin.isEmpty()) return remote
    val byId = builtin.associateBy { it.id }
    val byBase = builtin.groupBy { it.baseName }.mapValues { it.value.first() }
    return remote.map { voice ->
        if (voice.nameZh.isNotBlank() && voice.introZh.isNotBlank()) {
            voice
        } else {
            val match = byId[voice.id] ?: byBase[voice.baseName]
            voice.copy(
                nameZh = voice.nameZh.ifBlank { match?.nameZh.orEmpty() },
                introZh = voice.introZh.ifBlank { match?.introZh.orEmpty() },
            )
        }
    }
}
