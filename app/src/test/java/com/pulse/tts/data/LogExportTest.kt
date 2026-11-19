package com.pulse.tts.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportTest {

    private fun log(
        success: Boolean = true,
        fromNode: String? = null,
        error: String? = null,
        voice: String = "",
        rate: Int = 0,
        pitch: Int = 0,
        cooldown: Boolean = false,
        textPreview: String = "你好",
        textLen: Int = 2,
    ) = CallLog(
        time = 1_700_000_000_000,
        nodeUrl = "http://node:8080",
        mode = CallMode.AUTO,
        textLen = textLen,
        attempt = 2,
        success = success,
        latencyMs = 512,
        fromNode = fromNode,
        error = error,
        triggeredCooldown = cooldown,
        textPreview = textPreview,
        voice = voice,
        rate = rate,
        pitch = pitch,
    )

    @Test
    fun `复制文本按固定顺序包含完整字段`() {
        val text = formatLogForCopy(
            log(
                fromNode = "http://old",
                error = "HTTP 500",
                voice = "zh-CN-XiaoxiaoNeural",
                cooldown = true,
            ),
        )
        val lines = text.lines()
        assertEquals("调用记录", lines[0])
        assertEquals("结果：成功", lines[2])
        assertEquals("节点：http://node:8080", lines[3])
        assertEquals("切换来源：http://old", lines[4])
        assertTrue(lines[5].startsWith("模式：自动 · 第 2 次 · 耗时 512ms"))
        assertEquals("音色：zh-CN-XiaoxiaoNeural", lines[6])
        assertEquals("文本（2 字）：你好", lines[7])
        assertEquals("错误：HTTP 500", lines[8])
        assertEquals("冷却：触发 10 分钟", lines[9])
        assertFalse("复制文本不应以换行结尾", text.endsWith("\n"))
    }

    @Test
    fun `复制文本省略空字段与默认参数`() {
        val text = formatLogForCopy(log())
        val lines = text.lines()
        assertEquals(6, lines.size)
        assertFalse(text.contains("切换来源"))
        assertFalse(text.contains("音色"))
        assertFalse(text.contains("语速"))
        assertFalse(text.contains("错误"))
        assertFalse(text.contains("冷却"))
    }

    @Test
    fun `复制文本保留非零语速音调并标注截断`() {
        val text = formatLogForCopy(
            log(rate = 10, pitch = -5, textLen = 100, textPreview = "前八十个字"),
        )
        assertTrue(text.contains("语速：+10 · 音调：-5"))
        assertTrue(text.contains("文本（100 字）：前八十个字…"))
    }

    @Test
    fun `导出 JSON 结构完整且可选字段为 null`() {
        val json = Json.parseToJsonElement(
            exportLogsJson(
                listOf(
                    log(error = null, fromNode = null),
                    log(success = false, error = "HTTP 500", fromNode = "http://old"),
                ),
                exportedAt = "2026-11-19T12:44:26Z",
            ),
        ).jsonObject

        assertEquals("1", json["version"]!!.jsonPrimitive.content)
        assertEquals("2026-11-19T12:44:26Z", json["exportedAt"]!!.jsonPrimitive.content)
        assertEquals("2", json["count"]!!.jsonPrimitive.content)

        val items = json["logs"]!!.jsonArray
        assertEquals(2, items.size)
        val first = items[0].jsonObject
        assertEquals("AUTO", first["mode"]!!.jsonPrimitive.content)
        assertEquals("512", first["latencyMs"]!!.jsonPrimitive.content)
        assertEquals("null", first["fromNode"].toString())
        assertEquals("null", first["error"].toString())
        assertFalse(first["textTruncated"]!!.jsonPrimitive.content.toBoolean())

        val second = items[1].jsonObject
        assertEquals("HTTP 500", second["error"]!!.jsonPrimitive.content)
        assertEquals("http://old", second["fromNode"]!!.jsonPrimitive.content)
        assertEquals("false", second["success"]!!.jsonPrimitive.content)
    }

    @Test
    fun `导出 JSON 正确转义引号换行与反斜杠`() {
        val raw = "a\"b\nc\\d"
        val json = Json.parseToJsonElement(
            exportLogsJson(listOf(log(textPreview = raw, textLen = raw.length)), "now"),
        ).jsonObject
        val preview = json["logs"]!!.jsonArray[0].jsonObject["textPreview"]!!.jsonPrimitive.content
        assertEquals(raw, preview)
    }

    @Test
    fun `空列表导出 count 为零且 logs 为空数组`() {
        val json = Json.parseToJsonElement(exportLogsJson(emptyList(), "now")).jsonObject
        assertEquals("0", json["count"]!!.jsonPrimitive.content)
        assertEquals(0, json["logs"]!!.jsonArray.size)
    }
}
