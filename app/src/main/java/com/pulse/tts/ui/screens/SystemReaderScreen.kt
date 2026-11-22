package com.pulse.tts.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.CallMode
import com.pulse.tts.tts.SystemReadingPhase
import com.pulse.tts.tts.SystemReadingSession
import com.pulse.tts.tts.SystemReadingSnapshot
import com.pulse.tts.ui.LocalAppSettings
import com.pulse.tts.ui.components.ReaderStage
import com.pulse.tts.ui.components.ReaderTopBar
import com.pulse.tts.ui.components.SoundOrb
import kotlinx.coroutines.delay

/**
 * 系统朗读页：通知点击后打开，只读展示本次请求的合成状态。
 *
 * 播放控制权在调用方 App（系统 TTS 框架），这里不提供暂停、拖动、倍速、
 * 定时与停止，只展示节点、尝试次数、耗时与切换状态，完成后停留展示完成态。
 */
@Composable
fun SystemReaderScreen(
    onCollapse: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val snapshot by SystemReadingSession.snapshot.collectAsState()
    val appSettings = LocalAppSettings.current

    BackHandler(onBack = onCollapse)

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(snapshot?.phase, snapshot?.startedAtElapsedRealtime) {
        now = SystemClock.elapsedRealtime()
        if (snapshot?.phase == SystemReadingPhase.READING) {
            while (true) {
                delay(1_000)
                now = SystemClock.elapsedRealtime()
            }
        }
    }

    ReaderStage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReaderTopBar(
                title = snapshot?.let(::phaseTitle) ?: "系统朗读",
                statusColor = phaseColor(snapshot),
                onCollapse = onCollapse,
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Text(
                        "系统朗读 · 由调用方控制",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                SoundOrb(
                    isPlaying = snapshot?.phase == SystemReadingPhase.READING,
                    reduceMotion = appSettings.reduceMotion,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    snapshot?.let(::phaseHint) ?: "当前没有正在进行的系统朗读",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                val current = snapshot
                if (current != null) {
                    TextBody(
                        text = current.text,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.height(12.dp))
                    InfoRows(
                        snapshot = current,
                        elapsedMs = (now - current.startedAtElapsedRealtime).coerceAtLeast(0L),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            snapshot?.error?.let { error ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            snapshot?.let { current ->
                when (current.phase) {
                    SystemReadingPhase.COMPLETED -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已交给系统播放 · 总耗时 ${formatDuration(now - current.startedAtElapsedRealtime)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenLogs) {
                            Text("查看记录")
                        }
                    }

                    SystemReadingPhase.READING -> Text(
                        "暂停或停止请在调用方 App 中操作",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SystemReadingPhase.FAILED,
                    SystemReadingPhase.STOPPED,
                    -> Unit
                }
            }
        }
    }
}

@Composable
private fun TextBody(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        SelectionContainer {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun InfoRows(snapshot: SystemReadingSnapshot, elapsedMs: Long) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InfoRow("节点", snapshot.nodeLabel ?: "选择中…")
        if (snapshot.attempt > 0 && snapshot.total > 0) {
            InfoRow("尝试", "第 ${snapshot.attempt}/${snapshot.total} 个节点")
        }
        InfoRow("耗时", formatDuration(elapsedMs))
        snapshot.switchingTo?.let { InfoRow("状态", it) }
        snapshot.mode?.let { InfoRow("模式", modeLabel(it)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun phaseTitle(snapshot: SystemReadingSnapshot): String {
    val node = snapshot.nodeLabel
    return when (snapshot.phase) {
        SystemReadingPhase.READING -> if (node != null) "正在朗读 · $node" else "正在朗读"
        SystemReadingPhase.COMPLETED -> if (node != null) "朗读完成 · $node" else "朗读完成"
        SystemReadingPhase.FAILED -> "朗读失败"
        SystemReadingPhase.STOPPED -> "朗读已停止"
    }
}

private fun phaseHint(snapshot: SystemReadingSnapshot): String = when (snapshot.phase) {
    SystemReadingPhase.READING -> "正在合成并交给系统播放"
    SystemReadingPhase.COMPLETED -> "本次请求已完成"
    SystemReadingPhase.FAILED -> "本次朗读未能完成"
    SystemReadingPhase.STOPPED -> "朗读已被停止"
}

@Composable
private fun phaseColor(snapshot: SystemReadingSnapshot?): Color = when (snapshot?.phase) {
    SystemReadingPhase.FAILED -> MaterialTheme.colorScheme.error
    SystemReadingPhase.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.secondary
}

private fun modeLabel(mode: CallMode): String = when (mode) {
    CallMode.AUTO -> "自动"
    CallMode.GROUP -> "轮询"
    CallMode.MANUAL -> "手选"
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1_000).coerceAtLeast(0)
    return if (seconds < 60) "${seconds} 秒" else "${seconds / 60} 分 ${seconds % 60} 秒"
}
