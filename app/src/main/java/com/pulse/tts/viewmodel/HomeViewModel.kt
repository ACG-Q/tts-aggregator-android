package com.pulse.tts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.tts.data.CallMode
import com.pulse.tts.data.CallLog
import com.pulse.tts.data.LogStore
import com.pulse.tts.data.NodePool
import com.pulse.tts.data.NodeRepository
import com.pulse.tts.data.PoolConfig
import com.pulse.tts.data.SettingsStore
import com.pulse.tts.data.SynthPrefs
import com.pulse.tts.data.TtsNode
import com.pulse.tts.data.Voice
import com.pulse.tts.data.VoiceRepository
import com.pulse.tts.playback.ReadTuning
import kotlinx.coroutines.CancellationException
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
            try {
                val s = synthSettings.value
                val result = try {
                    pool.synthesize(
                        text,
                        SynthPrefs(voice = s.voice, rate = s.rate, pitch = s.pitch, style = s.style),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
                onResult(result)
            } finally {
                _speaking.value = false
            }
        }
    }

    // ---------- 调用逻辑配置 ----------

    fun setMode(mode: CallMode) {
        settings.updatePoolConfig { it.copy(mode = mode) }
    }

    fun cycleRate() {
        settings.updateSynthSettings { it.copy(rate = ReadTuning.nextRate(it.rate)) }
    }

    fun cyclePitch() {
        settings.updateSynthSettings { it.copy(pitch = ReadTuning.nextPitch(it.pitch)) }
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
