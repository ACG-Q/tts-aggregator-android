package com.example.ttsaggregator.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 统一 JSON 实例：忽略未知字段，容错解析 */
val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/** OkHttp Call 的协程挂起版（替代官方未提供的 await，约 15 行） */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { cancel() }
}

/** URL 工具 */
fun String.removeTrailingSlash(): String = trimEnd('/')

fun String.isValidUrl(): Boolean =
    (startsWith("http://") || startsWith("https://")) && length > "https://".length
