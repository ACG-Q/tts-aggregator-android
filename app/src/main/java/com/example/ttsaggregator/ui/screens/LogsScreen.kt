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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.CallLog
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.LogFilter
import com.example.ttsaggregator.data.Voice
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.EmptyState
import com.example.ttsaggregator.ui.components.SelectChip
import com.example.ttsaggregator.ui.theme.StatusColors
import com.example.ttsaggregator.ui.util.formatClock
import com.example.ttsaggregator.ui.util.formatLatency
import com.example.ttsaggregator.ui.util.shortUrl
import com.example.ttsaggregator.ui.util.voiceDisplayName
import com.example.ttsaggregator.viewmodel.LogsViewModel

/**
 * 调用日志详情：过滤（全部/成功/失败/切换）+ 每条含耗时、模式、
 * 尝试次数、文本长度、切换来源、错误详情、冷却触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val vm: LogsViewModel = appViewModel { LogsViewModel(it.logStore, it.voiceRepository) }
    val logs by vm.logList.collectAsState()
    val filter by vm.filter.collectAsState()
    val voices by vm.voiceList.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调用日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.clearLogs()
                        onToast("日志已清空")
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 过滤芯片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectChip("全部", selected = filter == LogFilter.ALL, onClick = { vm.setFilter(LogFilter.ALL) })
                SelectChip("成功", selected = filter == LogFilter.OK, onClick = { vm.setFilter(LogFilter.OK) })
                SelectChip("失败", selected = filter == LogFilter.FAIL, onClick = { vm.setFilter(LogFilter.FAIL) })
                SelectChip("切换", selected = filter == LogFilter.SWITCH, onClick = { vm.setFilter(LogFilter.SWITCH) })
            }

            if (logs.isEmpty()) {
                EmptyState("暂无日志", Modifier.padding(horizontal = 12.dp, vertical = 24.dp))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogDetailCard(log, voices)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogDetailCard(log: CallLog, voices: List<Voice>) {
    AppCard {
        // 头部：时间 + 节点 + 结果
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatClock(log.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                shortUrl(log.nodeUrl),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                if (log.success) "✓ 成功" else "✗ 失败",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (log.success) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }

        // 文本内容预览
        if (log.textPreview.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "💬 ${log.textPreview}${if (log.textLen > log.textPreview.length) "…" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }

        // 元数据标签
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MetaTag("耗时 ${formatLatency(log.latencyMs)}")
            MetaTag(
                when (log.mode) {
                    CallMode.AUTO -> "⚡自动"
                    CallMode.GROUP -> "🔁组轮询"
                    CallMode.MANUAL -> "🎯手选"
                },
            )
            MetaTag("第 ${log.attempt} 次尝试")
            MetaTag("文本 ${log.textLen} 字")
            if (log.triggeredCooldown) MetaTag("触发冷却 10min", warn = true)
        }

        // 音色与参数
        if (log.voice.isNotBlank() || log.rate != 0 || log.pitch != 0) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (log.voice.isNotBlank()) MetaTag("🎭 ${voiceDisplayName(voices, log.voice)}")
                if (log.rate != 0) MetaTag("语速 ${if (log.rate > 0) "+" else ""}${log.rate}")
                if (log.pitch != 0) MetaTag("音调 ${if (log.pitch > 0) "+" else ""}${log.pitch}")
            }
        }

        // 切换来源
        log.fromNode?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "已从 ${shortUrl(it, 30)} 切换至此节点",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        // 错误详情
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
            fontWeight = FontWeight.SemiBold,
            color = if (warn) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
