package com.pulse.tts.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

private val logTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

@Serializable
private data class ExportedLog(
    val time: String,
    val timeMillis: Long,
    val success: Boolean,
    val nodeUrl: String,
    val fromNode: String? = null,
    val mode: String,
    val attempt: Int,
    val latencyMs: Long,
    val textLen: Int,
    val textPreview: String,
    val textTruncated: Boolean,
    val voice: String,
    val rate: Int,
    val pitch: Int,
    val triggeredCooldown: Boolean,
    val error: String? = null,
)

@Serializable
private data class ExportedLogBundle(
    val version: Int = 1,
    val exportedAt: String,
    val count: Int,
    val logs: List<ExportedLog>,
)

private const val MILLIS_PER_SECOND = 1000L

/** 单条记录的整段可复制文本；空字段与默认参数整行省略。 */
fun formatLogForCopy(log: CallLog): String = buildString {
    appendLine("调用记录")
    appendLine("时间：${formatLogTime(log.time)}")
    appendLine("结果：${if (log.success) "成功" else "失败"}")
    appendLine("节点：${log.nodeUrl}")
    log.fromNode?.takeIf { it.isNotBlank() }?.let { appendLine("切换来源：$it") }
    appendLine(
        "模式：${modeLabel(log.mode)} · 第 ${log.attempt} 次 · " +
            "耗时 ${formatLatencyText(log.latencyMs)}",
    )
    if (log.voice.isNotBlank()) appendLine("音色：${log.voice}")
    if (log.rate != 0 || log.pitch != 0) {
        appendLine("语速：${signed(log.rate)} · 音调：${signed(log.pitch)}")
    }
    if (log.textPreview.isNotBlank()) {
        val ellipsis = if (log.textLen > log.textPreview.length) "…" else ""
        appendLine("文本（${log.textLen} 字）：${log.textPreview}$ellipsis")
    }
    log.error?.takeIf { it.isNotBlank() }?.let { appendLine("错误：$it") }
    if (log.triggeredCooldown) appendLine("冷却：触发 10 分钟")
}.trimEnd()

/** 导出选中的日志为 JSON；字段与复制文本一致，另带时间戳与截断标记。 */
fun exportLogsJson(logs: List<CallLog>, exportedAt: String): String =
    exportJson.encodeToString(
        ExportedLogBundle(
            exportedAt = exportedAt,
            count = logs.size,
            logs = logs.map { it.toExportedLog() },
        ),
    )

private fun CallLog.toExportedLog(): ExportedLog = ExportedLog(
    time = formatLogTime(time),
    timeMillis = time,
    success = success,
    nodeUrl = nodeUrl,
    fromNode = fromNode?.takeIf { it.isNotBlank() },
    mode = mode.name,
    attempt = attempt,
    latencyMs = latencyMs,
    textLen = textLen,
    textPreview = textPreview,
    textTruncated = textLen > textPreview.length,
    voice = voice,
    rate = rate,
    pitch = pitch,
    triggeredCooldown = triggeredCooldown,
    error = error?.takeIf { it.isNotBlank() },
)

private fun formatLogTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(logTimeFormatter)

private fun modeLabel(mode: CallMode): String = when (mode) {
    CallMode.AUTO -> "自动"
    CallMode.GROUP -> "组轮询"
    CallMode.MANUAL -> "手选"
}

private fun formatLatencyText(ms: Long): String =
    if (ms < MILLIS_PER_SECOND) "${ms}ms"
    else String.format(Locale.ROOT, "%.1fs", ms.toDouble() / MILLIS_PER_SECOND)

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()
