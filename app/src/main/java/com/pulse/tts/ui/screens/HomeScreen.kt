package com.pulse.tts.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pulse.tts.playback.PlaybackController
import com.pulse.tts.playback.ReadTuning
import com.pulse.tts.tts.AggregatorTtsService
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.PulseIconTile
import com.pulse.tts.ui.components.PulseMainHeader
import com.pulse.tts.ui.theme.LocalGlass
import com.pulse.tts.ui.theme.StatusColors
import com.pulse.tts.ui.util.EngineStatus
import com.pulse.tts.ui.util.engineStatus
import com.pulse.tts.ui.util.engineStatusLabel
import com.pulse.tts.ui.util.voiceDisplayName
import com.pulse.tts.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

/**
 * 首页：朗读优先。输入→选声音语速→开始朗读；播放态由全屏声场承接。 */
@Composable
fun HomeScreen(
    player: PlaybackController,
    onToast: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenReader: () -> Unit,
) {
    val vm: HomeViewModel = appViewModel {
        HomeViewModel(it.nodeRepository, it.nodePool, it.settings, it.logStore, it.voiceRepository)
    }
    val speaking by vm.speaking.collectAsState()
    val settings by vm.synthSettings.collectAsState()
    val voices by vm.voiceList.collectAsState()
    val playback by player.state.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var showEmptyError by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var engineState by remember { mutableStateOf(readEngineStatus(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            engineState = readEngineStatus(context)
            delay(2_000)
        }
    }

    val charCount = text.count { !it.isWhitespace() }
    val estimateSeconds = maxOf(10, (charCount / 4.2f).toInt())

    fun startReading() {
        if (text.isBlank()) {
            showEmptyError = true
            onToast("先写点什么，再开始朗读")
            return
        }
        showEmptyError = false
        vm.speak(text) { result ->
            result
                .onSuccess { bytes ->
                    val title = text.lineSequence().firstOrNull()?.trim()?.take(24).orEmpty()
                    player.play(
                        bytes = bytes,
                        title = title.ifBlank { "朗读" },
                        text = text,
                    )
                    onOpenReader()
                }
                .onFailure { onToast("朗读失败：${it.message ?: "未知错误"}") }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        PulseMainHeader(
            title = "Pulse",
            subtitle = "聚合朗读 · 让声音流动",
            leading = { PulseIconTile(Icons.Rounded.GraphicEq, size = 38, shape = CircleShape) },
            actions = {
                EngineStatusPill(
                    status = engineState,
                    onClick = onOpenPermissions,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "设置",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 76.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(
                "今日朗读",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "把文字交给声音，\n剩下的路慢慢走。",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "粘贴一篇文章，自动挑选此刻最稳的节点，一句一句读给你听。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (showEmptyError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 12.dp,
                    ),
                ) {
                    Box(modifier = Modifier.heightIn(min = 122.dp)) {
                        if (text.isBlank()) {
                            Text(
                                "粘贴一篇文章，或在这里写下想听的内容…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = {
                                text = it.take(8000)
                                if (it.isNotBlank()) showEmptyError = false
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$charCount 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(6.dp))
                        Box(
                            Modifier
                                .size(3.dp)
                                .background(MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "约 ${formatEstimate(estimateSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "粘贴",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val clip = clipboard.getText()?.text.orEmpty()
                                    if (clip.isBlank()) {
                                        onToast("剪贴板里没有文本")
                                    } else {
                                        text = clip.take(8000)
                                        showEmptyError = false
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        Text(
                            "清空",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    text = ""
                                    showEmptyError = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    if (showEmptyError) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "先写点什么，再开始朗读。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectorChip(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    label = voiceDisplayName(voices, settings.voice, settings.style),
                    onClick = onOpenVoice,
                )
                SelectorChip(
                    icon = Icons.Rounded.Speed,
                    label = "语速 ${ReadTuning.rateLabel(settings.rate)}",
                    onClick = vm::cycleRate,
                )
                SelectorChip(
                    icon = Icons.Rounded.GraphicEq,
                    label = "音调 ${ReadTuning.pitchLabel(settings.pitch)}",
                    onClick = vm::cyclePitch,
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !speaking) { startReading() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (speaking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (speaking) "正在合成…" else "开始朗读",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "文本仅在本机与所选节点之间传输",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (playback.isActive) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "继续听",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            playback.isPlaying -> "正在播放"
                            playback.isCompleted -> "已播完"
                            else -> "已暂停"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onOpenReader,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                playback.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                "点击回到声场",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "打开朗读页",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SelectorChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatEstimate(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun EngineStatusPill(
    status: EngineStatus,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val accent = when (status) {
        EngineStatus.ACTIVE -> StatusColors.Ok
        EngineStatus.READY -> MaterialTheme.colorScheme.primary
        EngineStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container = if (glass.enabled) {
        glass.cardColor
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            engineStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

private fun readEngineStatus(context: Context): EngineStatus {
    val isDefault = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.TTS_DEFAULT_SYNTH,
    ) == context.packageName
    return engineStatus(isDefault, AggregatorTtsService.isRunning)
}
