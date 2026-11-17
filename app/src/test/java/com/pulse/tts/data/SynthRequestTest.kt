package com.pulse.tts.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SynthRequestTest {

    private fun prefs(voice: String = "", rate: Int = 0, pitch: Int = 0, style: String = "") =
        SynthPrefs(voice = voice, rate = rate, pitch = pitch, style = style)

    @Test
    fun `默认请求把 rate 与 pitch 发成字符串`() {
        val json = buildSynthJson("你好", prefs(rate = 10, pitch = -5), "zh-CN-XiaoxiaoNeural")

        assertTrue("rate 必须是字符串，节点只接受字符串数字：$json", json.contains("\"rate\":\"10\""))
        assertTrue("pitch 必须是字符串，节点只接受字符串数字：$json", json.contains("\"pitch\":\"-5\""))
    }

    @Test
    fun `数字回退请求把 rate 与 pitch 发成数字`() {
        val json = buildSynthJson(
            "你好",
            prefs(rate = 10, pitch = -5),
            "zh-CN-XiaoxiaoNeural",
            numericRate = true,
        )

        assertTrue(json.contains("\"rate\":10"))
        assertTrue(json.contains("\"pitch\":-5"))
    }

    @Test
    fun `音色为空时使用节点默认音色`() {
        val json = buildSynthJson("你好", prefs(), "zh-CN-XiaoxiaoNeural")

        assertTrue(json.contains("\"voice\":\"zh-CN-XiaoxiaoNeural\""))
    }

    @Test
    fun `偏好音色优先于节点默认音色`() {
        val json = buildSynthJson("你好", prefs(voice = "zh-CN-YunxiNeural"), "zh-CN-XiaoxiaoNeural")

        assertTrue(json.contains("\"voice\":\"zh-CN-YunxiNeural\""))
        assertEquals(-1, json.indexOf("zh-CN-XiaoxiaoNeural"))
    }

    @Test
    fun `空风格不写入请求体`() {
        val json = buildSynthJson("你好", prefs(), "")

        assertTrue(!json.contains("\"style\""))
    }

    @Test
    fun `普通网络异常转成 null`() = runTest {
        val result = catchingCancellable<String> { throw IOException("boom") }

        assertNull(result)
    }

    @Test
    fun `取消异常原样抛出，不被当成节点失败`() = runTest {
        try {
            catchingCancellable<String> { throw CancellationException("cancelled") }
            fail("取消异常必须向上传播")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
