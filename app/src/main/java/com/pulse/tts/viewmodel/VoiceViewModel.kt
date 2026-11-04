package com.pulse.tts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.tts.data.SettingsStore
import com.pulse.tts.data.SynthSettings
import com.pulse.tts.data.Voice
import com.pulse.tts.data.VoiceRefreshResult
import com.pulse.tts.data.VoiceRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 声音选择器：音色列表加载 + 主动更新 + 语音参数（语速/音调/音色）。
 */
class VoiceViewModel(
    private val voices: VoiceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val voiceList: StateFlow<List<Voice>> = voices.voices
    val loading: StateFlow<Boolean> = voices.loading
    val synthSettings: StateFlow<SynthSettings> = settings.synthSettings

    fun loadVoices(force: Boolean = false) {
        viewModelScope.launch { voices.load(force = force) }
    }

    /** 强制联网更新，结果回调在协程结束时触发。 */
    fun refresh(onResult: (VoiceRefreshResult) -> Unit) {
        viewModelScope.launch { onResult(voices.refresh()) }
    }

    fun updateSettings(transform: (SynthSettings) -> SynthSettings) {
        settings.updateSynthSettings(transform)
    }
}
