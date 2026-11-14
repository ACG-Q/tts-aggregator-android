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
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val raw = response.body?.string().orEmpty()
            return parseUpdateInfo(raw) ?: throw IOException("更新信息格式无效")
        }
    }

    companion object {
        /** 恒定地址：始终指向最新正式 Release 的 update.json */
        const val MANIFEST_URL =
            "https://github.com/acg-q/tts-aggregator-android/releases/latest/download/update.json"

        const val RELEASE_PAGE =
            "https://github.com/acg-q/tts-aggregator-android/releases/latest"

        internal const val USER_AGENT = "Pulse-Android"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
