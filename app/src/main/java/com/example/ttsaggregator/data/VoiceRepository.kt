package com.example.ttsaggregator.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer

/**
 * 音色仓库：全局一份音色列表（不按节点冗余）。
 *
 * - 缓存：内存 + SharedPreferences（跨启动复用）
 * - 网络：[TtsApiClient]，新老端点回退
 * - 以 [StateFlow] 暴露列表与加载状态，UI 直接 collect。
 */
class VoiceRepository(
    private val settings: SettingsStore,
    private val api: TtsApiClient,
    private val nodes: () -> List<TtsNode>, // 惰性取节点：避免构造期环依赖
) {

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /** 冷启动时从缓存恢复 */
    init {
        decodeCache(settings.readRaw(SettingsStore.KEY_VOICES_CACHE))?.let { _voices.value = it }
    }

    /**
     * 加载音色列表。
     * @param source 指定数据来源节点；为空时用第一个健康节点
     * @param force  跳过内存缓存强制重新拉取
     */
    suspend fun load(source: TtsNode? = null, force: Boolean = false) {
        if (_loading.value) return
        if (!force && _voices.value.isNotEmpty()) return
        _loading.value = true
        try {
            val node = source ?: nodes().firstOrNull { it.health == TtsNode.Health.OK } ?: return
            val list = api.fetchVoices(node.baseUrl)
            if (!list.isNullOrEmpty()) {
                _voices.value = list
                settings.writeRaw(
                    SettingsStore.KEY_VOICES_CACHE,
                    AppJson.encodeToString(ListSerializer(Voice.serializer()), list),
                )
            }
        } finally {
            _loading.value = false
        }
    }

    private fun decodeCache(raw: String?): List<Voice>? = raw
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { AppJson.decodeFromString(ListSerializer(Voice.serializer()), it) }.getOrNull() }
        ?.takeIf { it.isNotEmpty() }
}
