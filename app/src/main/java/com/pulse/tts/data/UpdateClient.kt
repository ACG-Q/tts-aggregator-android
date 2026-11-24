package com.pulse.tts.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 拉取 Release 资产中的更新清单。
 *
 * 只做单次请求，代理/直连的回退由 [UpdateRepository] 编排；
 * 网络异常与非 2xx 统一抛出 [IOException]，交给上层决定是否提示。
 */
class UpdateClient(
    val http: OkHttpClient = defaultHttpClient(),
) {

    suspend fun fetch(url: String): UpdateInfo {
        val raw = fetchText(url)
        return parseUpdateInfo(raw) ?: throw IOException("更新信息格式无效")
    }

    /** 拉取任意 GitHub 文本接口（更新清单或 Release 列表）。 */
    suspend fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        /** 恒定地址：始终指向最新正式 Release 的 update.json */
        const val MANIFEST_URL =
            "https://github.com/acg-q/tts-aggregator-android/releases/latest/download/update.json"

        /** Release 列表（含预发布），供「全部版本」渠道发现 beta。 */
        const val RELEASES_API =
            "https://api.github.com/repos/acg-q/tts-aggregator-android/releases?per_page=30"

        const val RELEASE_PAGE =
            "https://github.com/acg-q/tts-aggregator-android/releases/latest"

        /** 全部版本列表页，供更新弹窗跳转。 */
        const val ALL_RELEASES_PAGE =
            "https://github.com/acg-q/tts-aggregator-android/releases"

        internal const val USER_AGENT = "Pulse-Android"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
