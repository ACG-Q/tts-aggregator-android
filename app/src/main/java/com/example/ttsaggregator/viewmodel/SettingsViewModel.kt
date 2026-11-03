package com.example.ttsaggregator.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ttsaggregator.data.AppSettings
import com.example.ttsaggregator.data.AppSettingsOptions
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.LogStore
import com.example.ttsaggregator.data.NodeRepository
import com.example.ttsaggregator.data.SettingsStore
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.data.VoiceRepository
import com.example.ttsaggregator.data.parseNodeBundle
import com.example.ttsaggregator.playback.ReadTuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页状态与配置写入。
 *
 * 所有配置都通过 [SettingsStore] 原子更新，主题等全局状态由 MainActivity 直接订阅，
 * 因此这里不需要额外缓存；节点与日志数量直接暴露仓库的 StateFlow。
 */
class SettingsViewModel(
    private val repo: NodeRepository,
    private val settings: SettingsStore,
    private val logs: LogStore,
    private val voices: VoiceRepository,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.appSettings
    val synthSettings = settings.synthSettings
    val poolConfig = settings.poolConfig
    val nodes = repo.nodes
    val logCount = logs.count
    val voiceList: StateFlow<List<Voice>> = voices.voices

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    fun updateApp(transform: (AppSettings) -> AppSettings) {
        settings.updateAppSettings(transform)
    }

    fun cycleTheme() = updateApp { it.copy(theme = it.theme.next()) }

    fun stepRate(delta: Int) = settings.updateSynthSettings {
        it.copy(rate = stepOption(it.rate, ReadTuning.RATES, delta))
    }

    fun stepPitch(delta: Int) = settings.updateSynthSettings {
        it.copy(pitch = stepOption(it.pitch, ReadTuning.PITCHES, delta))
    }

    fun stepCooldown(delta: Int) = updateApp {
        it.copy(cooldownMinutes = stepOption(it.cooldownMinutes, AppSettingsOptions.COOLDOWN_MINUTES, delta))
    }

    fun stepFailThreshold(delta: Int) = updateApp {
        it.copy(failThreshold = stepOption(it.failThreshold, AppSettingsOptions.FAIL_THRESHOLDS, delta))
    }

    /**
     * 睡眠定时是「点一下换一个档位」的循环控件，必须能回到「关闭」。
     * 用 [cycleOption] 而不是 [stepOption]：后者到 60 分钟就夹死，关闭档位再也点不回来。
     */
    fun cycleSleep() = updateApp {
        it.copy(sleepMinutes = cycleOption(it.sleepMinutes, AppSettingsOptions.SLEEP_MINUTES))
    }

    fun cycleMode() = settings.updatePoolConfig { it.copy(mode = it.mode.nextMode()) }

    fun setMode(mode: CallMode) = settings.updatePoolConfig { it.copy(mode = mode) }

    fun clearLogs() = logs.clear()

    fun importNodes(raw: String, onDone: (String) -> Unit) {
        if (_importing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _importing.value = true
            val message = try {
                val bundle = parseNodeBundle(raw)
                val drafts = bundle.drafts
                if (drafts.isEmpty()) {
                    "没有可导入的有效节点"
                } else {
                    val existingUrls = repo.nodes.value.mapTo(mutableSetOf()) { it.baseUrl }
                    val unique = drafts.distinctBy { it.url.trimEnd('/') }
                    val added = unique.count { it.url.trimEnd('/') !in existingUrls }
                    repo.saveRawBatch(unique)
                    val restored = bundle.settings?.let { imported ->
                        settings.saveAppSettings(imported)
                        true
                    } ?: false
                    "导入完成：新增 $added 个，共 ${unique.size} 个" +
                        if (restored) "，已恢复偏好设置" else ""
                }
            } catch (e: Exception) {
                Log.w(TAG, "importNodes failed", e)
                "导入失败：${e.message ?: "未知错误"}"
            } finally {
                _importing.value = false
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    companion object {
        private const val TAG = "TTS-Agg"
    }
}

/** 在固定选项集中前后移动；未知值从第一项开始，越界时夹在两端。 */
internal fun <T> stepOption(current: T, options: List<T>, delta: Int): T {
    if (options.isEmpty()) return current
    val index = options.indexOf(current).takeIf { it >= 0 } ?: 0
    return options[(index + delta).coerceIn(0, options.lastIndex)]
}

/** 在固定选项集中向下循环；用于「点一下切下一档」且必须能回到首档的控件。 */
internal fun <T> cycleOption(current: T, options: List<T>): T {
    if (options.isEmpty()) return current
    val index = options.indexOf(current).takeIf { it >= 0 } ?: 0
    return options[(index + 1) % options.size]
}

internal fun CallMode.nextMode(): CallMode = when (this) {
    CallMode.AUTO -> CallMode.GROUP
    CallMode.GROUP -> CallMode.MANUAL
    CallMode.MANUAL -> CallMode.AUTO
}
