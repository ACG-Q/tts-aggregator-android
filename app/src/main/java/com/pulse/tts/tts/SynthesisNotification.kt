package com.pulse.tts.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pulse.tts.MainActivity
import com.pulse.tts.R
import com.pulse.tts.data.CallMode
import com.pulse.tts.data.SynthesisEvent
import java.util.Locale

internal data class NotificationState(
    val text: String,
    val mode: CallMode? = null,
    val nodeLabel: String? = null,
    val attempt: Int = 0,
    val total: Int = 0,
    val elapsedMs: Long? = null,
    val error: String? = null,
    val switchingTo: String? = null,
)

internal data class NotificationContent(
    val title: String,
    val body: String,
    val subText: String,
)

/** 纯文案构建，便于 JVM 单测；不依赖 Android 通知 API。 */
internal fun buildNotificationContent(state: NotificationState): NotificationContent {
    val title = when {
        state.error != null -> "朗读失败"
        state.nodeLabel != null -> "正在朗读 · ${state.nodeLabel}"
        else -> "正在选择节点…"
    }
    val parts = buildList {
        state.mode?.let { add(modeLabel(it)) }
        if (state.attempt > 0 && state.total > 0) add("第 ${state.attempt}/${state.total} 个节点")
        state.switchingTo?.let { add("正在切换到 $it") }
        state.elapsedMs?.let { add("已用 ${formatElapsed(it)}") }
        state.error?.let { add("失败原因：$it") }
    }
    return NotificationContent(
        title = title,
        body = "文本：${textPreview(state.text)}",
        subText = parts.joinToString(" · ").ifBlank { "系统朗读" },
    )
}

internal fun textPreview(text: String, limit: Int = 40): String {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    return if (normalized.length > limit) normalized.take(limit) + "…" else normalized
}

private fun modeLabel(mode: CallMode): String = when (mode) {
    CallMode.AUTO -> "自动"
    CallMode.GROUP -> "轮询"
    CallMode.MANUAL -> "手选"
}

private fun formatElapsed(ms: Long): String {
    if (ms < 60_000) {
        return String.format(Locale.ROOT, "%.1f 秒", ms / 1000.0)
    }
    val seconds = ms / 1000
    return "${seconds / 60} 分 ${seconds % 60} 秒"
}

/**
 * 系统 TTS 前台通知：合成开始时创建，失败/成功/停止后移除。
 *
 * 所有系统通知调用都做异常兜底，通知失败不能影响朗读本身。
 */
internal class SynthesisNotification(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private var state = NotificationState(text = "")

    fun start(text: String): Notification {
        state = NotificationState(text = text)
        return build()
    }

    fun onEvent(event: SynthesisEvent): Notification {
        state = when (event) {
            is SynthesisEvent.AttemptStarted -> state.copy(
                mode = event.mode,
                nodeLabel = event.node.label.ifBlank { event.node.baseUrl },
                attempt = event.attempt,
                total = event.total,
                elapsedMs = null,
                error = null,
                switchingTo = null,
            )
            is SynthesisEvent.AttemptFailed -> state.copy(
                mode = event.mode,
                nodeLabel = event.node.label.ifBlank { event.node.baseUrl },
                error = event.error,
                switchingTo = if (event.willRetry) "下一个节点" else null,
                elapsedMs = null,
            )
            is SynthesisEvent.Succeeded -> state.copy(
                mode = event.mode,
                nodeLabel = event.node.label.ifBlank { event.node.baseUrl },
                elapsedMs = event.elapsedMs,
                error = null,
                switchingTo = null,
            )
            is SynthesisEvent.Failed -> state.copy(
                mode = event.mode,
                error = event.error,
                switchingTo = null,
            )
            SynthesisEvent.Offline -> state.copy(
                error = "当前网络不可用，已暂停节点切换",
                switchingTo = null,
            )
        }
        return build()
    }

    fun post(notification: Notification) {
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun handleEvent(event: SynthesisEvent) {
        post(onEvent(event))
    }

    fun finish() {
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun build(): Notification {
        val content = buildNotificationContent(state)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tts)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setSubText(content.subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "tts_system_playback"
        const val NOTIFICATION_ID = 4101

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "系统朗读",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "系统 TTS 调用时显示当前节点与朗读进度"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
