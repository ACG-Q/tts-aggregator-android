package com.pulse.tts.viewmodel

import androidx.lifecycle.ViewModel
import com.pulse.tts.data.CallLog
import com.pulse.tts.data.LogFilter
import com.pulse.tts.data.LogStore
import com.pulse.tts.data.Voice
import com.pulse.tts.data.VoiceRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 调用日志页：过滤 + 清空。列表本身由 LogStore 的 StateFlow 驱动。
 */
class LogsViewModel(
    private val logs: LogStore,
    voices: VoiceRepository,
) : ViewModel() {

    val logList: StateFlow<List<CallLog>> = logs.logs
    val filter: StateFlow<LogFilter> = logs.filter
    val voiceList: StateFlow<List<Voice>> = voices.voices

    fun setFilter(f: LogFilter) = logs.setFilter(f)

    fun clearLogs() = logs.clear()
}
