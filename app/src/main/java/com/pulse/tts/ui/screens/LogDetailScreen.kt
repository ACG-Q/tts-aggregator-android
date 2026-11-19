package com.pulse.tts.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.CallLog
import com.pulse.tts.data.CallMode
import com.pulse.tts.ui.components.AppCard
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.util.formatClock
import com.pulse.tts.ui.util.formatLatency
import com.pulse.tts.ui.util.voiceDisplayName

/**
 * 单条调用记录详情：展示列表里被截断或未呈现的完整字段。
 * 节点地址与文本支持长按选择复制，便于排查失败原因。
 */
@Composable
fun LogDetailScreen(
    log: CallLog,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "记录详情",
                subtitle = formatClock(log.time, "yyyy-MM-dd HH:mm:ss"),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResultCard(log)

            SectionCard("节点") {
                SelectionContainer {
                    Text(
                        log.nodeUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (log.fromNode != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "由 ${log.fromNode} 切换而来",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            TextCard(log)

            SectionCard("合成参数") {
                DetailRow("音色", voiceDisplayName(emptyList(), log.voice))
                DetailRow(
                    "语速",
                    if (log.rate == 0) "默认 0" else "${if (log.rate > 0) "+" else ""}${log.rate}",
                )
                DetailRow(
                    "音调",
                    if (log.pitch == 0) "默认 0" else "${if (log.pitch > 0) "+" else ""}${log.pitch}",
                )
            }

            if (log.error != null) {
                AppCard(contentPadding = PaddingValues(16.dp)) {
                    Text(
                        "错误详情",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    ) {
                        SelectionContainer {
                            Text(
                                log.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(log: CallLog) {
    val accent = if (log.success) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.error

    AppCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            if (log.success) "成功" else "失败",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DetailTag("耗时 ${formatLatency(log.latencyMs)}")
            DetailTag(
                when (log.mode) {
                    CallMode.AUTO -> "自动"
                    CallMode.GROUP -> "组轮询"
                    CallMode.MANUAL -> "手选"
                },
            )
            DetailTag("第 ${log.attempt} 次")
            DetailTag("${log.textLen} 字")
        }
        if (log.triggeredCooldown) {
            Spacer(Modifier.height(6.dp))
            DetailTag("触发冷却 10min", warn = true)
        }
    }
}

@Composable
private fun TextCard(log: CallLog) {
    if (log.textPreview.isBlank()) return

    val truncated = log.textLen > log.textPreview.length
    AppCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "朗读文本",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (truncated) "前 ${log.textPreview.length} 字 · 共 ${log.textLen} 字"
                else "${log.textLen} 字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(
                "「${log.textPreview}」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DetailTag(text: String, warn: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (warn) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (warn) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
