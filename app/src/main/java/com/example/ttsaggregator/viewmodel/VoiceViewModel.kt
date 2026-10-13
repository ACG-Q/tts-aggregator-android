package com.example.ttsaggregator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ttsaggregator.data.SettingsStore
import com.example.ttsaggregator.data.SynthSettings
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.data.VoiceRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 语音设置页：音色列表加载 + 语音参数（语速/音调/音色）。
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

    fun updateSettings(transform: (SynthSettings) -> SynthSettings) {
        settings.updateSynthSettings(transform)
    }
}
