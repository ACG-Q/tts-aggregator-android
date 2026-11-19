package com.pulse.tts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.CallLog
import com.pulse.tts.data.CallMode
import com.pulse.tts.data.LogFilter
import com.pulse.tts.data.Voice
import com.pulse.tts.data.exportLogsJson
import com.pulse.tts.ui.LocalAppSettings
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.AppCard
import com.pulse.tts.ui.components.EmptyState
import com.pulse.tts.ui.components.HeaderIcon
import com.pulse.tts.ui.components.LogsSelectionBar
import com.pulse.tts.ui.components.NodeSelectBox
import com.pulse.tts.ui.components.PulseIconTile
import com.pulse.tts.ui.components.PulseMainHeader
import com.pulse.tts.ui.components.SelectChip
import com.pulse.tts.ui.util.formatClock
import com.pulse.tts.ui.util.formatLatency
import com.pulse.tts.ui.util.invertSelection
import com.pulse.tts.ui.util.isAllSelected
import com.pulse.tts.ui.util.shortUrl
import com.pulse.tts.ui.util.toggleSelection
import com.pulse.tts.ui.util.voiceDisplayName
import com.pulse.tts.viewmodel.LogsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * 记录一级页：每次节点调用的成功、失败、切换与耗时。
 * 过滤维度由 LogStore 维护，切换即异步重查，不在 UI 层自行过滤。
 *
 * 多选支持顶部图标与长按卡片两种入口；选中记录可导出为 JSON。
 */
@Composable
fun LogsScreen(
    onToast: (String) -> Unit,
    onOpenDetail: (CallLog) -> Unit,
) {
    val vm: LogsViewModel = appViewModel { LogsViewModel(it.logStore, it.voiceRepository) }
    val logs by vm.logList.collectAsState()
    val filter by vm.filter.collectAsState()
    val voices by vm.voiceList.collectAsState()

    val context = LocalContext.current
    val appSettings = LocalAppSettings.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingExport by remember { mutableStateOf<List<CallLog>>(emptyList()) }

    fun exitSelection() {
        selectMode = false
        selected = emptySet()
    }

    fun toggle(id: Long) {
        selected = toggleSelection(selected, id)
    }

    fun enterSelectionWith(id: Long) {
        if (appSettings.haptics) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (selectMode) {
            toggle(id)
        } else {
            selectMode = true
            selected = setOf(id)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = pendingExport
        pendingExport = emptyList()
        if (uri != null && export.isNotEmpty()) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val out = context.contentResolver.openOutputStream(uri)
                            ?: error("无法写入文件")
                        out.bufferedWriter().use {
                            it.write(exportLogsJson(export, Instant.now().toString()))
                        }
                    }.isSuccess
                }
                if (ok) {
                    onToast("已导出 ${export.size} 条记录")
                    exitSelection()
                } else {
                    onToast("导出失败，请重试")
                }
            }
        }
    }

    BackHandler(enabled = selectMode) { exitSelection() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            PulseMainHeader(
                title = "记录",
                subtitle = if (selectMode) {
                    "已选 ${selected.size} 条 · 长按或点击继续选择"
                } else {
                    "最近 ${logs.size} 条调用 · 成功、失败与节点切换"
                },
                leading = { PulseIconTile(Icons.Rounded.ReceiptLong, size = 38, shape = CircleShape) },
                actions = {
                    HeaderIcon(
                        icon = Icons.Rounded.Checklist,
                        description = if (selectMode) "退出多选" else "多选",
                        active = selectMode,
                        onClick = { if (selectMode) exitSelection() else selectMode = true },
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = {
                        vm.clearLogs()
                        onToast("记录已清空")
                        exitSelection()
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
                SelectChip("全部", filter == LogFilter.ALL, onClick = {
                    exitSelection()
                    vm.setFilter(LogFilter.ALL)
                })
                SelectChip("成功", filter == LogFilter.OK, onClick = {
                    exitSelection()
                    vm.setFilter(LogFilter.OK)
                })
                SelectChip("失败", filter == LogFilter.FAIL, onClick = {
                    exitSelection()
                    vm.setFilter(LogFilter.FAIL)
                })
                SelectChip("切换", filter == LogFilter.SWITCH, onClick = {
                    exitSelection()
                    vm.setFilter(LogFilter.SWITCH)
                })
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
                        bottom = if (selectMode) 140.dp else 76.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogSummaryCard(
                            log = log,
                            voices = voices,
                            selectMode = selectMode,
                            checked = log.id in selected,
                            onClick = {
                                if (selectMode) toggle(log.id) else onOpenDetail(log)
                            },
                            onLongClick = { enterSelectionWith(log.id) },
                            onToggleCheck = { toggle(log.id) },
                        )
                    }
                }
            }
        }

        if (selectMode) {
            val ids = logs.map { it.id }
            LogsSelectionBar(
                selectedCount = selected.size,
                allSelected = isAllSelected(ids, selected),
                onSelectAll = {
                    selected = if (isAllSelected(ids, selected)) emptySet() else ids.toSet()
                },
                onInvert = { selected = invertSelection(ids, selected) },
                onExport = {
                    val export = logs.filter { it.id in selected }
                    if (export.isNotEmpty()) {
                        pendingExport = export
                        exportLauncher.launch(
                            "tts-logs-${formatClock(System.currentTimeMillis(), "yyyy-MM-dd-HHmm")}.json",
                        )
                    }
                },
                onExit = { exitSelection() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun LogSummaryCard(
    log: CallLog,
    voices: List<Voice>,
    selectMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCheck: () -> Unit,
) {
    AppCard(
        onClick = onClick,
        onLongClick = onLongClick,
        containerColor = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectMode) {
                NodeSelectBox(checked = checked, onClick = onToggleCheck)
                Spacer(Modifier.width(10.dp))
            } else {
                Text(
                    formatClock(log.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
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
