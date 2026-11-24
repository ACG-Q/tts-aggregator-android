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

    /** 冷启动静默检查：节流通过时拉取清单，按渠道与关闭记忆决定是否提示。 */
    suspend fun checkOnLaunch() {
        val now = System.currentTimeMillis()
        if (!shouldCheckOnLaunch(settings.readLastUpdateCheck(), now)) return

        val info = fetchInfo() ?: return
        // 只有成功拿到清单才占用节流窗口，断网不会让下次启动白等 6 小时
        settings.saveLastUpdateCheck(now)

        val appSettings = settings.appSettings.value
        val decision = decideLaunchUpdate(
            currentVersionCode = currentVersionCode,
            info = info,
            dismissedCode = appSettings.dismissedUpdateCode,
            dismissedAt = appSettings.dismissedUpdateAt,
            now = now,
        ) ?: return

        when (decision) {
            is UpdateDecision.Required -> _state.update { it.copy(forced = decision.info) }
            is UpdateDecision.Optional -> _state.update { it.copy(optional = decision.info) }
            UpdateDecision.UpToDate -> Unit
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
            settings.saveLastUpdateCheck(System.currentTimeMillis())
            when (val decision = decideUpdate(currentVersionCode, info)) {
                is UpdateDecision.Required -> _state.update { it.copy(forced = decision.info) }
                is UpdateDecision.Optional -> _state.update { it.copy(optional = decision.info) }
                UpdateDecision.UpToDate -> emit("已是最新版本")
            }
        } finally {
            _state.update { it.copy(checking = false) }
        }
    }

    /** 关闭可选更新弹窗，并记住该版本以静默 6 小时。 */
    fun dismissOptional() {
        val info = _state.value.optional
        if (info != null) {
            val at = System.currentTimeMillis()
            settings.updateAppSettings {
                it.copy(dismissedUpdateCode = info.versionCode, dismissedUpdateAt = at)
            }
        }
        _state.update { it.copy(optional = null) }
    }

    /** 切换更新渠道：保存后清空节流并立即检查一次。 */
    suspend fun applyChannel(channel: UpdateChannel) {
        if (settings.appSettings.value.updateChannel == channel) return
        settings.updateAppSettings { it.copy(updateChannel = channel) }
        settings.saveLastUpdateCheck(0L)
        checkOnLaunch()
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
        if (settings.appSettings.value.updateChannel == UpdateChannel.ALL) {
            fetchNewestFromReleases()?.let { return it }
        }
        return fetchWithFallback(UpdateClient.MANIFEST_URL) { client.fetch(it) }
    }

    /** 全部版本渠道：找出含 update.json 的最新几个 release，取 versionCode 最大者。 */
    private suspend fun fetchNewestFromReleases(): UpdateInfo? {
        val raw = fetchWithFallback(UpdateClient.RELEASES_API) { client.fetchText(it) } ?: return null
        val candidates = selectManifestCandidates(parseReleaseList(raw))
        val infos = candidates.mapNotNull { url ->
            fetchWithFallback(url) { client.fetch(it) }
        }
        return pickNewestInfo(infos)
    }

    /** 加速地址优先、直连兜底；全部失败返回 null。 */
    private suspend fun <T> fetchWithFallback(url: String, fetch: suspend (String) -> T): T? {
        for (candidate in candidateUrls(url)) {
            try {
                return fetch(candidate)
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
