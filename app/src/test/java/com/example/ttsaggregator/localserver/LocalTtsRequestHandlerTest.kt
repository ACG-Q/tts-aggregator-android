package com.example.ttsaggregator.localserver

import com.example.ttsaggregator.data.SynthPrefs
import com.example.ttsaggregator.data.SynthSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTtsRequestHandlerTest {

    private val defaults = SynthSettings(rate = 10, pitch = -5, voice = "default-voice")
    private val mp3 = byteArrayOf(0x49, 0x44, 0x33) // "ID3"

    private fun handler(
        synth: (String, SynthPrefs) -> Result<ByteArray> = { _, _ -> Result.success(mp3) },
    ) = LocalTtsRequestHandler({ defaults }, synth)

    // ---------- GET ----------

    @Test
    fun `GET 用 t 参数取文本并返回 MP3 字节`() {
        val r = handler().handleGet(mapOf("t" to listOf("你好")))
        assertArrayEquals(mp3, (r as TtsResult.Audio).bytes)
    }

    @Test
    fun `GET 只给 text 参数也可识别`() {
        val r = handler().handleGet(mapOf("text" to listOf("你好")))
        assertTrue(r is TtsResult.Audio)
    }

    @Test
    fun `GET 完全缺参数返回 400`() {
        val r = handler().handleGet(emptyMap()) as TtsResult.Error
        assertEquals(400, r.code)
    }

    @Test
    fun `GET 空白文本返回 400`() {
        val r = handler().handleGet(mapOf("t" to listOf("   "))) as TtsResult.Error
        assertEquals(400, r.code)
    }

    @Test
    fun `GET 映射 v r p s 且越界钳制 非数字回退默认`() {
        var cap: SynthPrefs? = null
        handler { _, p ->
            cap = p
            Result.success(mp3)
        }.handleGet(
            mapOf(
                "t" to listOf("x"),
                "v" to listOf("voice-1"),
                "r" to listOf("200"),
                "p" to listOf("abc"),
                "s" to listOf("cheerful"),
            ),
        )
        val c = cap ?: error("未调用合成")
        assertEquals("voice-1", c.voice)
        assertEquals(100, c.rate)
        assertEquals(-5, c.pitch)
        assertEquals("cheerful", c.style)
    }

    @Test
    fun `GET 缺省参数回退应用默认`() {
        var cap: SynthPrefs? = null
        handler { _, p ->
            cap = p
            Result.success(mp3)
        }.handleGet(mapOf("t" to listOf("x")))
        val c = cap ?: error("未调用合成")
        assertEquals("default-voice", c.voice)
        assertEquals(10, c.rate)
        assertEquals(-5, c.pitch)
        assertEquals("", c.style)
    }

    @Test
    fun `合成失败映射为 502 并携带错误信息`() {
        val r = handler { _, _ -> Result.failure(RuntimeException("节点全部不可用")) }
            .handleGet(mapOf("t" to listOf("x"))) as TtsResult.Error
        assertEquals(502, r.code)
        assertTrue(r.message.contains("节点全部不可用"))
    }

    // ---------- POST ----------

    @Test
    fun `POST 完整 JSON 合成成功`() {
        var cap: SynthPrefs? = null
        val r = handler { _, p ->
            cap = p
            Result.success(mp3)
        }.handlePost("""{"text":"你好","voice":"v2","rate":30,"pitch":10,"style":"sad"}""")
        assertArrayEquals(mp3, (r as TtsResult.Audio).bytes)
        val c = cap ?: error("未调用合成")
        assertEquals("v2", c.voice)
        assertEquals(30, c.rate)
        assertEquals(10, c.pitch)
        assertEquals("sad", c.style)
    }

    @Test
    fun `POST 缺省字段回退应用默认`() {
        var cap: SynthPrefs? = null
        handler { _, p ->
            cap = p
            Result.success(mp3)
        }.handlePost("""{"text":"hi"}""")
        val c = cap ?: error("未调用合成")
        assertEquals("default-voice", c.voice)
        assertEquals(10, c.rate)
        assertEquals(-5, c.pitch)
        assertEquals("", c.style)
    }

    @Test
    fun `POST 非法 JSON 返回 400 并提示 JSON`() {
        val r = handler().handlePost("{oops") as TtsResult.Error
        assertEquals(400, r.code)
        assertTrue(r.message.contains("JSON"))
    }

    @Test
    fun `POST 缺 text 字段返回 400`() {
        val r = handler().handlePost("{}") as TtsResult.Error
        assertEquals(400, r.code)
    }

    @Test
    fun `POST 空文本返回 400`() {
        val r = handler().handlePost("""{"text":"  "}""") as TtsResult.Error
        assertEquals(400, r.code)
    }

    @Test
    fun `POST rate 越界钳制为 -100`() {
        var cap: SynthPrefs? = null
        handler { _, p ->
            cap = p
            Result.success(mp3)
        }.handlePost("""{"text":"x","rate":-999}""")
        val c = cap ?: error("未调用合成")
        assertEquals(-100, c.rate)
    }
}
