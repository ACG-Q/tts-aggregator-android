package com.example.ttsaggregator.localserver

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

/**
 * 本机 TTS HTTP 服务：仅监听 127.0.0.1，路由 /tts。
 * HTTP 适配与业务解析分离——参数/JSON/错误映射全在 [LocalTtsRequestHandler]。
 */
class LocalTtsHttpServer(
    port: Int,
    private val handler: LocalTtsRequestHandler,
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/tts" && session.method == Method.GET ->
                    toResponse(handler.handleGet(session.parameters))
                session.uri == "/tts" && session.method == Method.POST ->
                    toResponse(handler.handlePost(readBody(session)))
                else -> textResponse(404, "not found: ${session.uri}")
            }
        } catch (e: ResponseException) {
            textResponse(400, "请求解析失败：${e.message}")
        } catch (e: Exception) {
            textResponse(500, "服务内部错误：${e.message}")
        }
    }

    /** 按 Content-Length 读原始 body（JSON 可达整章文本，不走 parseBody 的缓冲） */
    private fun readBody(session: IHTTPSession): String {
        val len = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (len <= 0) return ""
        val buf = ByteArray(len)
        var off = 0
        val input = session.inputStream
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) break
            off += n
        }
        return String(buf, 0, off, Charsets.UTF_8)
    }

    private fun toResponse(result: TtsResult): Response = when (result) {
        is TtsResult.Audio -> NanoHTTPD.newFixedLengthResponse(
            HttpStatus(200, "OK"),
            "audio/mpeg",
            ByteArrayInputStream(result.bytes),
            result.bytes.size.toLong(),
        )
        is TtsResult.Error -> textResponse(result.code, result.message)
    }

    private fun textResponse(code: Int, message: String): Response {
        val bytes = message.toByteArray(Charsets.UTF_8)
        return NanoHTTPD.newFixedLengthResponse(
            HttpStatus(code, reason(code)),
            "text/plain; charset=utf-8",
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        )
    }

    private fun reason(code: Int): String = when (code) {
        400 -> "Bad Request"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        else -> "Error"
    }
}

/** NanoHTTPD 的 Status 枚举没有 502，自造 IStatus 以覆盖任意状态码 */
private class HttpStatus(
    private val code: Int,
    private val text: String,
) : NanoHTTPD.Response.IStatus {
    override fun getDescription(): String = "$code $text"
    override fun getRequestStatus(): Int = code
}
