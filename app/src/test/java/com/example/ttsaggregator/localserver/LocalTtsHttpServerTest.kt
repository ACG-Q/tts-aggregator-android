package com.example.ttsaggregator.localserver

import com.example.ttsaggregator.data.SynthSettings
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LocalTtsHttpServerTest {

    private val mp3 = byteArrayOf(0x49, 0x44, 0x33) // "ID3"
    private lateinit var server: LocalTtsHttpServer

    @Before
    fun setUp() {
        val handler = LocalTtsRequestHandler({ SynthSettings() }) { _, _ -> Result.success(mp3) }
        server = LocalTtsHttpServer(0, handler) // 端口 0 = 系统分配，避免测试间冲突
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun open(path: String): HttpURLConnection =
        URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection

    private fun post(body: String): HttpURLConnection {
        val conn = open("/tts")
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return conn
    }

    @Test
    fun `GET t 返回 200 和 MP3 字节`() {
        val conn = open("/tts?t=hello")
        assertEquals(200, conn.responseCode)
        assertTrue(conn.contentType.startsWith("audio/mpeg"))
        assertArrayEquals(mp3, conn.inputStream.readBytes())
    }

    @Test
    fun `POST JSON 返回 200 和 MP3 字节`() {
        val conn = post("""{"text":"你好"}""")
        assertEquals(200, conn.responseCode)
        assertArrayEquals(mp3, conn.inputStream.readBytes())
    }

    @Test
    fun `POST 缺 text 字段返回 400`() {
        assertEquals(400, post("{}").responseCode)
    }

    @Test
    fun `POST 非法 JSON 返回 400`() {
        assertEquals(400, post("{oops").responseCode)
    }

    @Test
    fun `未知路径返回 404`() {
        assertEquals(404, open("/nope").responseCode)
    }

    @Test
    fun `合成失败返回 502 并带错误信息`() {
        val handler = LocalTtsRequestHandler({ SynthSettings() }) { _, _ ->
            Result.failure(RuntimeException("节点全部不可用"))
        }
        val s = LocalTtsHttpServer(0, handler)
        s.start()
        try {
            val conn = URL("http://127.0.0.1:${s.listeningPort}/tts?t=x").openConnection() as HttpURLConnection
            assertEquals(502, conn.responseCode)
            val body = conn.errorStream.readBytes().decodeToString()
            assertTrue(body.contains("节点全部不可用"))
        } finally {
            s.stop()
        }
    }
}
