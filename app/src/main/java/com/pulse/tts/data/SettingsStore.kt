package com.pulse.tts.data

import android.content.Context
import com.pulse.tts.playback.PlaybackSnapshot
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

    // ---------- 应用级设置 ----------

    private val _appSettings = MutableStateFlow(read<AppSettings>(KEY_APP) ?: AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings

    fun saveAppSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_APP, json.encodeToString(AppSettings.serializer(), settings)).apply()
        _appSettings.value = settings
    }

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        synchronized(this) { saveAppSettings(transform(_appSettings.value)) }
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

    // ---------- 更新检查节流 ----------

    fun saveLastUpdateCheck(at: Long) {
        writeRaw(KEY_UPDATE_LAST_CHECK, at.toString())
    }

    fun readLastUpdateCheck(): Long =
        readRaw(KEY_UPDATE_LAST_CHECK)?.toLongOrNull() ?: 0L

    // ---------- 播放快照 ----------

    fun savePlaybackSnapshot(snapshot: PlaybackSnapshot) {
        writeRaw(KEY_PLAYBACK, encodePlaybackSnapshot(snapshot))
    }

    fun readPlaybackSnapshot(): PlaybackSnapshot? =
        readRaw(KEY_PLAYBACK)?.let(::decodePlaybackSnapshot)

    fun clearPlaybackSnapshot() {
        prefs.edit().remove(KEY_PLAYBACK).apply()
    }

    companion object {
        const val KEY_POOL = "pool_config"
        const val KEY_SETTINGS = "synth_settings"
        const val KEY_APP = "app_settings"
        const val KEY_VOICES_CACHE = "voices_cache"
        const val KEY_PLAYBACK = "playback_snapshot"
        const val KEY_UPDATE_LAST_CHECK = "update_last_check_at"

        private val snapshotJson = Json { ignoreUnknownKeys = true }

        fun encodePlaybackSnapshot(snapshot: PlaybackSnapshot): String =
            snapshotJson.encodeToString(PlaybackSnapshot.serializer(), snapshot)

        fun decodePlaybackSnapshot(raw: String): PlaybackSnapshot? =
            runCatching {
                snapshotJson.decodeFromString(PlaybackSnapshot.serializer(), raw)
            }.getOrNull()
    }
}
