package com.example.ttsaggregator.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.CallLog
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.LogFilter
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.EmptyState
import com.example.ttsaggregator.ui.components.PulseMainHeader
import com.example.ttsaggregator.ui.components.SelectChip
import com.example.ttsaggregator.ui.util.formatClock
import com.example.ttsaggregator.ui.util.formatLatency
import com.example.ttsaggregator.ui.util.shortUrl
import com.example.ttsaggregator.ui.util.voiceDisplayName
import com.example.ttsaggregator.viewmodel.LogsViewModel

/**
 * 记录一级页：每次节点调用的成功、失败、切换与耗时。
 * 过滤维度由 LogStore 维护，切换即异步重查，不在 UI 层自行过滤。
 */
@Composable
fun LogsScreen(
    onToast: (String) -> Unit,
) {
    val vm: LogsViewModel = appViewModel { LogsViewModel(it.logStore, it.voiceRepository) }
    val logs by vm.logList.collectAsState()
    val filter by vm.filter.collectAsState()
    val voices by vm.voiceList.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        PulseMainHeader(
            title = "记录",
            subtitle = "最近 ${logs.size} 条调用 · 成功、失败与节点切换",
            titleStyle = MaterialTheme.typography.headlineMedium,
            actions = {
                IconButton(onClick = {
                    vm.clearLogs()
                    onToast("记录已清空")
                }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "清空记录",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectChip("全部", filter == LogFilter.ALL, onClick = { vm.setFilter(LogFilter.ALL) })
            SelectChip("成功", filter == LogFilter.OK, onClick = { vm.setFilter(LogFilter.OK) })
            SelectChip("失败", filter == LogFilter.FAIL, onClick = { vm.setFilter(LogFilter.FAIL) })
            SelectChip("切换", filter == LogFilter.SWITCH, onClick = { vm.setFilter(LogFilter.SWITCH) })
        }

        if (logs.isEmpty()) {
            EmptyState(
                "还没有记录。开始第一次朗读后，这里会出现节点调用明细。",
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = 76.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs, key = { it.id }) { log ->
                    LogDetailCard(log, voices)
                }
            }
        }
    }
}

@Composable
private fun LogDetailCard(log: CallLog, voices: List<Voice>) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatClock(log.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                shortUrl(log.nodeUrl),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                if (log.success) "成功" else "失败",
                style = MaterialTheme.typography.labelMedium,
                color = if (log.success) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error,
            )
        }

        if (log.textPreview.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "「${log.textPreview}${if (log.textLen > log.textPreview.length) "…" else ""}」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MetaTag("耗时 ${formatLatency(log.latencyMs)}")
            MetaTag(
                when (log.mode) {
                    CallMode.AUTO -> "自动"
                    CallMode.GROUP -> "组轮询"
                    CallMode.MANUAL -> "手选"
                },
            )
            MetaTag("第 ${log.attempt} 次")
            MetaTag("${log.textLen} 字")
            if (log.triggeredCooldown) MetaTag("触发冷却 10min", warn = true)
        }

        if (log.voice.isNotBlank() || log.rate != 0 || log.pitch != 0) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (log.voice.isNotBlank()) MetaTag(voiceDisplayName(voices, log.voice))
                if (log.rate != 0) MetaTag("语速 ${if (log.rate > 0) "+" else ""}${log.rate}")
                if (log.pitch != 0) MetaTag("音调 ${if (log.pitch > 0) "+" else ""}${log.pitch}")
            }
        }

        log.fromNode?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "已从 ${shortUrl(it, 30)} 切换至此节点",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        log.error?.let {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun MetaTag(text: String, warn: Boolean = false) {
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
