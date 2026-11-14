package com.pulse.tts.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.tts.data.UpdateRepository
import com.pulse.tts.data.UpdateState
import com.pulse.tts.ui.util.ApkInstaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 更新功能的 ViewModel：检查与下载都委托给单例 [UpdateRepository]，
 * 界面只观察 [state]，下载完成后在这里拉起系统安装器。
 */
class UpdateViewModel(
    private val repository: UpdateRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = repository.state
    val messages: SharedFlow<String> = repository.messages

    private var downloadJob: Job? = null

    fun checkManually() {
        viewModelScope.launch { repository.checkManually() }
    }

    fun downloadAndInstall(context: Context) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            val file = repository.download() ?: return@launch
            when (ApkInstaller.install(context.applicationContext, file)) {
                ApkInstaller.Result.NEED_PERMISSION -> repository.emit("请允许安装未知应用后重试")
                ApkInstaller.Result.FAILED -> repository.emit("无法启动安装器")
                ApkInstaller.Result.STARTED -> Unit
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun dismissOptional() = repository.dismissOptional()

    fun clearDownloadFailure() = repository.clearDownloadFailure()

    override fun onCleared() {
        downloadJob?.cancel()
        super.onCleared()
    }
}
