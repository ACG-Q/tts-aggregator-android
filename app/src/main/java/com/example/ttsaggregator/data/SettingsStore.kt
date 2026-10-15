package com.example.ttsaggregator.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * 小配置存储（SharedPreferences，JSON 序列化）。
 *
 * 只放"体积小、整体读写"的配置：
 * - 调用逻辑配置 [PoolConfig]
 * - 语音参数 [SynthSettings]
 *
 * 两者都以 [StateFlow] 暴露，UI 直接 collect，无需手动同步。
 * 大数据（节点、日志、音色缓存）不放这里，见对应 Store/Repository。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tts_config", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // ---------- 调用逻辑配置 ----------

    private val _poolConfig = MutableStateFlow(read(KEY_POOL) ?: PoolConfig())
    val poolConfig: StateFlow<PoolConfig> = _poolConfig

    fun savePoolConfig(config: PoolConfig) {
        prefs.edit().putString(KEY_POOL, json.encodeToString(PoolConfig.serializer(), config)).apply()
        _poolConfig.value = config
    }

    /** 原子更新：读-改-写在同一把锁内，避免并发丢失更新 */
    fun updatePoolConfig(transform: (PoolConfig) -> PoolConfig) {
        synchronized(this) { savePoolConfig(transform(_poolConfig.value)) }
    }

    // ---------- 语音参数 ----------

    private val _synthSettings = MutableStateFlow(read(KEY_SETTINGS) ?: SynthSettings())
    val synthSettings: StateFlow<SynthSettings> = _synthSettings

    fun saveSynthSettings(settings: SynthSettings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(SynthSettings.serializer(), settings)).apply()
        _synthSettings.value = settings
    }

    fun updateSynthSettings(transform: (SynthSettings) -> SynthSettings) {
        synchronized(this) { saveSynthSettings(transform(_synthSettings.value)) }
    }

    // ---------- 本机 HTTP 服务 ----------

    private val _localServerConfig = MutableStateFlow(read(KEY_LOCAL_SERVER) ?: LocalServerConfig())
    val localServerConfig: StateFlow<LocalServerConfig> = _localServerConfig

    fun saveLocalServerConfig(config: LocalServerConfig) {
        prefs.edit().putString(KEY_LOCAL_SERVER, json.encodeToString(LocalServerConfig.serializer(), config)).apply()
        _localServerConfig.value = config
    }

    // ---------- 通用 JSON 读写（音色缓存等外部数据复用） ----------

    private inline fun <T> read(key: String, decode: (String) -> T): T? =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { decode(raw) }.getOrNull()
        }

    private inline fun <reified T> read(key: String): T? = read(key) { s ->
        json.decodeFromString(serializer<T>(), s)
    }

    fun writeRaw(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun readRaw(key: String): String? = prefs.getString(key, null)

    companion object {
        const val KEY_POOL = "pool_config"
        const val KEY_SETTINGS = "synth_settings"
        const val KEY_VOICES_CACHE = "voices_cache"
        const val KEY_LOCAL_SERVER = "local_server"
    }
}
