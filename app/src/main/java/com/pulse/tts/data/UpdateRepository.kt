package com.pulse.tts.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * 更新检查的界面状态。
 *
 * - [optional]：可关闭的可选更新弹窗
 * - [forced]：不可关闭的强制更新弹窗
 * - [downloadFailedPage]：下载失败时供 UI 打开浏览器的回退地址
 */
data class UpdateState(
    val checking: Boolean = false,
    val optional: UpdateInfo? = null,
    val forced: UpdateInfo? = null,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val downloadFailedPage: String? = null,
)

/**
 * 更新检查与下载编排。
 *
 * 请求顺序固定为「加速地址 → 直连」，任何一步失败都继续下一步；
 * 启动检查带 6 小时节流，手动检查始终执行。网络问题永不阻断应用使用。
 */
class UpdateRepository(
    private val settings: SettingsStore,
    private val client: UpdateClient,
    private val downloader: UpdateDownloader,
    private val currentVersionCode: Int,
    private val deviceAbis: List<String>,
) {

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 冷启动静默检查：节流通过时拉取清单，仅强制更新进入状态。 */
    suspend fun checkOnLaunch() {
        val now = System.currentTimeMillis()
        if (!shouldCheckOnLaunch(settings.readLastUpdateCheck(), now)) return
        settings.saveLastUpdateCheck(now)

        val info = fetchInfo() ?: return
        val decision = decideUpdate(currentVersionCode, info)
        if (decision is UpdateDecision.Required) {
            _state.update { it.copy(forced = decision.info) }
        }
    }

    /** 手动检查：绕过节流，结果通过弹窗或 [messages] 反馈。 */
    suspend fun checkManually() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        try {
            val info = fetchInfo()
            if (info == null) {
                emit("检查更新失败，请检查网络或 GitHub 加速地址")
                return
            }
            when (val decision = decideUpdate(currentVersionCode, info)) {
                is UpdateDecision.Required -> _state.update { it.copy(forced = decision.info) }
                is UpdateDecision.Optional -> _state.update { it.copy(optional = decision.info) }
                UpdateDecision.UpToDate -> emit("已是最新版本")
            }
        } finally {
            _state.update { it.copy(checking = false) }
        }
    }

    fun dismissOptional() {
        _state.update { it.copy(optional = null) }
    }

    /** 下载当前弹窗对应的 APK；失败时写入 [UpdateState.downloadFailedPage]。 */
    suspend fun download(): File? {
        val info = _state.value.forced ?: _state.value.optional ?: return null
        val target = pickApk(info, deviceAbis)
        if (target == null) {
            emit("没有适用于本机的安装包")
            _state.update { it.copy(downloadFailedPage = releasePageOf(info)) }
            return null
        }

        _state.update { it.copy(downloading = true, progress = null, downloadFailedPage = null) }
        try {
            for (url in candidateUrls(target.url)) {
                try {
                    return downloader.download(url, target.abi, info.sha256[target.abi]) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 加速地址失败后继续尝试直连
                }
            }
            emit("下载失败，已打开下载页")
            _state.update { it.copy(downloadFailedPage = releasePageOf(info)) }
            return null
        } finally {
            _state.update { it.copy(downloading = false, progress = null) }
        }
    }

    fun clearDownloadFailure() {
        _state.update { it.copy(downloadFailedPage = null) }
    }

    fun emit(message: String) {
        _messages.tryEmit(message)
    }

    private suspend fun fetchInfo(): UpdateInfo? {
        for (url in candidateUrls(UpdateClient.MANIFEST_URL)) {
            try {
                return client.fetch(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 代理失败后继续直连
            }
        }
        return null
    }

    private fun candidateUrls(url: String): List<String> {
        val proxy = normalizeGithubProxy(settings.appSettings.value.githubProxy)
        return buildList {
            if (!proxy.isNullOrBlank()) add(buildProxiedUrl(proxy, url))
            add(url)
        }
    }

    private fun releasePageOf(info: UpdateInfo): String =
        info.releasePage.takeIf { it.isNotBlank() } ?: UpdateClient.RELEASE_PAGE
}
