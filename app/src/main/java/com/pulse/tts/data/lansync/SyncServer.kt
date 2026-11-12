package com.pulse.tts.data.lansync

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

data class IncomingRequest(val from: PeerInfo, val nodes: List<SyncedNode>)

sealed class SyncDecision {
    data class Accept(val nodes: List<SyncedNode>, val applied: Int) : SyncDecision()
    object Reject : SyncDecision()
}

/**
 * 按需启动的极简 HTTP/1.1 服务端，只处理 ping 与 sync 两个端点。
 * 一次请求一个连接，读完即关，不处理 keep-alive 与分块传输。
 */
class SyncServer(
    private val deviceName: String,
    private val sid: String,
    private val localNodes: () -> List<SyncedNode>,
    private val confirm: suspend (IncomingRequest) -> SyncDecision?,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false
    private var socket: ServerSocket? = null

    fun start(): Int {
        val server = ServerSocket(0)
        socket = server
        running = true
        pool.execute {
            while (running) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(client) } }
            }
        }
        return server.localPort
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        client.use { conn ->
            conn.soTimeout = 10_000
            val input = conn.getInputStream()
            val requestLine = readLine(input) ?: return
            var contentLength = 0
            var session: String? = null
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val index = line.indexOf(':')
                if (index <= 0) continue
                when (line.substring(0, index).trim().lowercase()) {
                    "content-length" -> contentLength = line.substring(index + 1).trim().toIntOrNull() ?: 0
                    "x-sync-session" -> session = line.substring(index + 1).trim()
                }
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?')

            when {
                method == "GET" && path == "/v1/ping" -> {
                    val body = json.encodeToString(
                        PingResponse.serializer(),
                        PingResponse(name = deviceName, nodes = localNodes().size),
                    )
                    respond(conn.getOutputStream(), 200, body)
                }
                method == "POST" && path == "/v1/sync" -> handleSync(conn.getOutputStream(), input, session, contentLength)
                else -> respond(conn.getOutputStream(), 404, json.encodeToString(ErrorBody.serializer(), ErrorBody("not_found")))
            }
        }
    }

    private fun handleSync(output: OutputStream, input: InputStream, session: String?, contentLength: Int) {
        if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
            respond(output, 413, json.encodeToString(ErrorBody.serializer(), ErrorBody("too_large")))
            return
        }
        // 先把请求体读完再判定，避免提前关闭连接触发对端 RST。
        val bytes = readExactly(input, contentLength)
        if (session != sid) {
            respond(output, 403, json.encodeToString(ErrorBody.serializer(), ErrorBody("rejected")))
            return
        }
        val request = runCatching {
            json.decodeFromString(SyncRequest.serializer(), bytes.toString(Charsets.UTF_8))
        }.getOrNull()
        if (request == null) {
            respond(output, 400, json.encodeToString(ErrorBody.serializer(), ErrorBody("invalid_body")))
            return
        }
        if (request.nodes.size > MAX_NODES) {
            respond(output, 413, json.encodeToString(ErrorBody.serializer(), ErrorBody("too_large")))
            return
        }
        val decision = runBlocking {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { confirm(IncomingRequest(request.from, request.nodes)) }
        }
        when (decision) {
            null -> respond(output, 408, json.encodeToString(ErrorBody.serializer(), ErrorBody("timeout")))
            is SyncDecision.Reject -> respond(output, 403, json.encodeToString(ErrorBody.serializer(), ErrorBody("rejected")))
            is SyncDecision.Accept -> respond(
                output, 200,
                json.encodeToString(SyncResponse.serializer(), SyncResponse(decision.nodes, decision.applied)),
            )
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (buffer.isEmpty()) null else buffer.toString()
            when (byte) {
                '\r'.code -> continue
                '\n'.code -> return buffer.toString()
                else -> buffer.append(byte.toChar())
            }
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return if (read == length) buffer else buffer.copyOf(read)
    }

    private fun respond(output: OutputStream, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${reason(status)}\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        408 -> "Request Timeout"
        413 -> "Payload Too Large"
        else -> "Error"
    }
}
