package com.example.ttsaggregator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.CallLog
import com.example.ttsaggregator.data.LogStore
import com.example.ttsaggregator.data.NodePool
import com.example.ttsaggregator.data.NodeRepository
import com.example.ttsaggregator.data.PoolConfig
import com.example.ttsaggregator.data.SettingsStore
import com.example.ttsaggregator.data.SynthPrefs
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.data.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首页：朗读 + 调用逻辑配置 + 调度状态 + 最近调用。
 * 只暴露首页需要的状态与动作，其余一概不知。
 */
class HomeViewModel(
    private val repo: NodeRepository,
    private val pool: NodePool,
    private val settings: SettingsStore,
    logs: LogStore,
    private val voices: VoiceRepository,
) : ViewModel() {

    val nodes: StateFlow<List<TtsNode>> = repo.nodes
    val poolConfig: StateFlow<PoolConfig> = settings.poolConfig
    val synthSettings = settings.synthSettings
    val voiceList: StateFlow<List<Voice>> = voices.voices

    /** 最近 3 条调用（日志写入即自动刷新） */
    val recentLogs: StateFlow<List<CallLog>> = logs.logs.map { it.take(RECENT_LOG_COUNT) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    init {
        viewModelScope.launch { voices.load() } // 冷启动拉一次音色（有缓存则秒回）
    }

    // ---------- 朗读（走节点池，成功/失败均刷新日志与节点状态） ----------

    fun speak(text: String, onResult: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _speaking.value = true
            val s = synthSettings.value
            val result = runCatching {
                pool.synthesize(text, SynthPrefs(rate = s.rate, pitch = s.pitch))
            }.getOrElse { Result.failure(it) }
            _speaking.value = false
            onResult(result)
        }
    }

    // ---------- 调用逻辑配置 ----------

    fun setMode(mode: CallMode) {
        settings.updatePoolConfig { it.copy(mode = mode) }
    }

    fun resetManualFailCount() {
        settings.updatePoolConfig { it.copy(manualFailCount = 0) }
    }

    // ---------- 调度状态派生 ----------

    fun manualNode(): TtsNode? = nodes.value.find { it.id == poolConfig.value.manualId }

    fun manualStopped(): Boolean = poolConfig.value.manualFailCount >= MANUAL_FAIL_LIMIT

    fun activeCandidateCount(): Int = nodes.value.count {
        it.health != TtsNode.Health.FAIL && !it.disabled && !it.isCoolingDown()
    }

    companion object {
        const val MANUAL_FAIL_LIMIT = 3
        private const val RECENT_LOG_COUNT = 3
    }
}
