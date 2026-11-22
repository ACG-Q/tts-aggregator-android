package com.pulse.tts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.tts.playback.PlaybackController
import com.pulse.tts.playback.ReadingTimeline
import com.pulse.tts.ui.LocalAppSettings
import com.pulse.tts.ui.components.ReaderChip
import com.pulse.tts.ui.components.ReaderStage
import com.pulse.tts.ui.components.ReaderTopBar
import com.pulse.tts.ui.components.SoundOrb

private val READER_SPEEDS = listOf(0.8f, 1f, 1.2f, 1.5f)
private val SLEEP_OPTIONS = listOf(0, 15, 30, 60)

/**
 * 朗读态：整屏声场 + 逐句高亮 + 可拖动进度 + 倍速 / 定时 / 停止。
 * 文案按字符比例映射播放进度，不依赖节点返回的时间戳。
 */
@Composable
fun ReadingScreen(
    controller: PlaybackController,
    onCollapse: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val appSettings = LocalAppSettings.current
    val sentences = remember(state.text) { ReadingTimeline.split(state.text) }
    val sentence = remember(sentences, state.progress) {
        ReadingTimeline.sentenceAt(sentences, state.progress)
    }
    var sleepMinutes by remember { mutableIntStateOf(appSettings.sleepMinutes) }

    BackHandler(onBack = onCollapse)

    val statusLabel = when {
        state.error != null -> "播放出错"
        state.isPlaying -> "正在朗读"
        state.isCompleted -> "朗读完成"
        state.isActive -> "已暂停"
        else -> "朗读"
    }

    ReaderStage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReaderTopBar(
                title = "$statusLabel · ${state.title}",
                statusColor = MaterialTheme.colorScheme.secondary,
                onCollapse = onCollapse,
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Text(
                        "本机文本 · 自动调度",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                SoundOrb(
                    isPlaying = state.isPlaying,
                    reduceMotion = appSettings.reduceMotion,
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = sentence.ifBlank { state.title },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "${ReadingTimeline.format(state.positionMs)}  /  ${ReadingTimeline.format(state.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.progress,
                    onValueChange = { value ->
                        controller.seekTo((value * state.durationMs).toLong())
                    },
                    valueRange = 0f..1f,
                    enabled = state.durationMs > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        controller.seekTo(state.positionMs - 10_000L)
                    },
                ) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "后退 10 秒")
                }
                Spacer(Modifier.size(20.dp))
                Surface(
                    onClick = controller::toggle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(68.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "继续朗读",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.size(20.dp))
                IconButton(
                    onClick = {
                        controller.seekTo(state.positionMs + 10_000L)
                    },
                ) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "前进 10 秒")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ReaderChip(
                    text = "%.1f×".format(state.speed),
                    icon = {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = {
                        val index = READER_SPEEDS.indexOfFirst { it == state.speed }
                        val next = READER_SPEEDS[(index + 1).mod(READER_SPEEDS.size)]
                        controller.setSpeed(next)
                    },
                )
                Spacer(Modifier.size(8.dp))
                ReaderChip(
                    text = if (sleepMinutes == 0) "定时" else "${sleepMinutes} 分钟",
                    selected = sleepMinutes > 0,
                    icon = {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = {
                        val index = SLEEP_OPTIONS.indexOf(sleepMinutes)
                        val next = SLEEP_OPTIONS[(index + 1).mod(SLEEP_OPTIONS.size)]
                        sleepMinutes = next
                        controller.setSleepTimer(next)
                    },
                )
                Spacer(Modifier.size(8.dp))
                ReaderChip(
                    text = "停止",
                    icon = {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = controller::stop,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "锁屏后仍可继续 · 失败自动切换节点",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
