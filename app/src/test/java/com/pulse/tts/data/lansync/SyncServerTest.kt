package com.pulse.tts.data.lansync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class SyncServerTest {

    private fun get(port: Int, path: String): Triple<Int, Map<String, String>, String> {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(
                "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(),
            )
            return readResponse(BufferedReader(InputStreamReader(socket.getInputStream())))
        }
    }

    private fun post(port: Int, path: String, sid: String, body: String): Triple<Int, Map<String, String>, String> {
        Socket("127.0.0.1", port).use { socket ->
            val bytes = body.toByteArray()
            val head = "POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\n" +
                "X-Sync-Session: $sid\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(head.toByteArray() + bytes)
            return readResponse(BufferedReader(InputStreamReader(socket.getInputStream())))
        }
    }

    private fun readResponse(reader: BufferedReader): Triple<Int, Map<String, String>, String> {
        val status = reader.readLine().split(' ')[1].toInt()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val index = line.indexOf(':')
            headers[line.substring(0, index).lowercase()] = line.substring(index + 1).trim()
        }
        return Triple(status, headers, reader.readText())
    }

    @Test
    fun `ping returns peer info`() {
        val server = SyncServer("测试机", "sid-1", { emptyList() }, { null })
        val port = server.start()
        try {
            val (status, _, body) = get(port, "/v1/ping")
            assertEquals(200, status)
            assertTrue(body.contains("\"name\":\"测试机\""))
            assertTrue(body.contains("\"platform\":\"android\""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `sync rejects wrong session`() {
        val server = SyncServer("测试机", "sid-1", { emptyList() }, { null })
        val port = server.start()
        try {
            val (status, _, _) = post(
                port, "/v1/sync", "wrong",
                """{"from":{"name":"电脑","platform":"desktop"},"nodes":[]}""",
            )
            assertEquals(403, status)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `sync accepts when user confirms`() = runBlocking {
        val server = SyncServer(
            "测试机", "sid-1", { emptyList() },
            { req -> SyncDecision.Accept(listOf(SyncedNode(url = "https://local.example.com")), req.nodes.size) },
        )
        val port = server.start()
        try {
            val (status, _, body) = post(
                port, "/v1/sync", "sid-1",
                """{"from":{"name":"电脑","platform":"desktop"},"nodes":[{"url":"https://a.example.com","label":"A"}]}""",
            )
            assertEquals(200, status)
            assertTrue(body.contains("local.example.com"))
        } finally {
            server.stop()
        }
    }
}
