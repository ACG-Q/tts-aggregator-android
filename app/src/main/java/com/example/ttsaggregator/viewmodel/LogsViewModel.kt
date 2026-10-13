package com.example.ttsaggregator.viewmodel

import androidx.lifecycle.ViewModel
import com.example.ttsaggregator.data.CallLog
import com.example.ttsaggregator.data.LogFilter
import com.example.ttsaggregator.data.LogStore
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.data.VoiceRepository
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
