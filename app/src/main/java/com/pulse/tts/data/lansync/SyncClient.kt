package com.pulse.tts.data.lansync

import com.pulse.tts.data.AppJson
import com.pulse.tts.data.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class SyncOutcome {
    data class Ok(val response: SyncResponse) : SyncOutcome()
    object Rejected : SyncOutcome()
    object Timeout : SyncOutcome()
    data class Failed(val code: Int) : SyncOutcome()
}

/** 出站同步：短超时，不重试（同步是用户主动触发，重试只会放大等待）。 */
class SyncClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun ping(peer: SyncPeer): PingResponse? = runCatching {
        val request = Request.Builder()
            .url("http://${peer.host}:${peer.port}/v1/ping")
            .get()
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) return@use null
            AppJson.decodeFromString(PingResponse.serializer(), response.body?.string().orEmpty())
        }
    }.getOrNull()

    suspend fun sync(peer: SyncPeer, from: PeerInfo, nodes: List<SyncedNode>): SyncOutcome =
        runCatching {
            val body = AppJson.encodeToString(
                SyncRequest.serializer(),
                SyncRequest(from = from, nodes = nodes),
            ).toRequestBody(jsonType)
            val request = Request.Builder()
                .url("http://${peer.host}:${peer.port}/v1/sync")
                .header("X-Sync-Session", peer.sid)
                .post(body)
                .build()
            http.newCall(request).await().use { response ->
                when (response.code) {
                    200 -> SyncOutcome.Ok(
                        AppJson.decodeFromString(SyncResponse.serializer(), response.body?.string().orEmpty()),
                    )
                    403 -> SyncOutcome.Rejected
                    408 -> SyncOutcome.Timeout
                    else -> SyncOutcome.Failed(response.code)
                }
            }
        }.getOrElse { SyncOutcome.Failed(-1) }
}
