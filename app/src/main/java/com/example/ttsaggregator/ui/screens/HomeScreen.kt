package com.example.ttsaggregator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.EmptyState
import com.example.ttsaggregator.ui.components.SectionHeader
import com.example.ttsaggregator.ui.util.formatLatency
import com.example.ttsaggregator.ui.util.shortUrl
import com.example.ttsaggregator.ui.util.voiceDisplayName
import com.example.ttsaggregator.viewmodel.HomeViewModel

/**
 * 首页 = 朗读 + 调用逻辑（含节点列表入口）+ 调度状态 + 日志简版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlay: (ByteArray) -> Unit,
    onToast: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenNodes: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val vm: HomeViewModel = appViewModel { HomeViewModel(it.nodeRepository, it.nodePool, it.settings, it.logStore, it.voiceRepository) }
    val speaking by vm.speaking.collectAsState()
    // rememberSaveable：旋转屏/后台回来不丢草稿
    var text by rememberSaveable { mutableStateOf("你好，这是 TTS Aggregator。") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS Aggregator") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- 朗读 ----
            item {
                AppCard {
                    SectionHeader("朗读文本")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("输入要朗读的文字…", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            vm.speak(text) { result ->
                                result
                                    .onSuccess(onPlay)
                                    .onFailure { onToast("朗读失败: ${it.message}") }
                            }
                        },
                        enabled = !speaking && text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (speaking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("立即朗读", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // ---- 语音设置入口 ----
            item { VoiceEntryRow(vm, onOpenVoice) }

            // ---- 调用逻辑（含节点列表入口） ----
            item { CallModeCard(vm, onOpenNodes) }

            // ---- 调度状态 ----
            item { SchedCard(vm) }

            // ---- 日志简版 ----
            item { RecentLogCard(vm, onOpenLogs) }
        }
    }
}

// ---------------------------------------------------------------- 语音设置入口

@Composable
private fun VoiceEntryRow(vm: HomeViewModel, onOpenVoice: () -> Unit) {
    val settings by vm.synthSettings.collectAsState()
    val voices by vm.voiceList.collectAsState()
    AppCard(onClick = onOpenVoice) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎭", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "语音设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    voiceDisplayName(voices, settings.voice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- 调用逻辑

@Composable
private fun CallModeCard(vm: HomeViewModel, onOpenNodes: () -> Unit) {
    val config by vm.poolConfig.collectAsState()
    // 收集节点列表：manualNode()/activeCandidateCount() 依赖它触发重组
    val nodes by vm.nodes.collectAsState()

    AppCard {
        SectionHeader("调用逻辑", hint = "失败自动容灾")
        Spacer(Modifier.height(8.dp))

        ModeSegment(mode = config.mode, onModeChange = vm::setMode)
        Spacer(Modifier.height(8.dp))
        Text(
            when (config.mode) {
                CallMode.AUTO -> "按「健康 → 最近测试」顺序尝试，失败自动切换；连续失败 3 次自动冷却 10 分钟。"
                CallMode.GROUP -> "组内成员轮流使用，每次成功后游标前进，避免单节点被封。"
                CallMode.MANUAL -> "只用指定节点；连续失败 3 次自动停止（状态卡可重置）。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        // 节点列表入口
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Text("节点列表", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                when (config.mode) {
                    CallMode.AUTO -> "${vm.activeCandidateCount()} 个候选 · 顺序=调度顺序 ›"
                    CallMode.GROUP -> "已选 ${config.groupIds.size} 个 · 去列表勾选 ›"
                    CallMode.MANUAL ->
                        vm.manualNode()?.let { "手选 ${it.label.ifBlank { shortUrl(it.baseUrl, 16) }} ›" }
                            ?: "未选手选节点 ›"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(2.dp))
        TextButton(onClick = onOpenNodes, modifier = Modifier.fillMaxWidth()) {
            Text("打开节点列表 ›", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ModeSegment(mode: CallMode, onModeChange: (CallMode) -> Unit) {
    val options = listOf(CallMode.AUTO to "⚡ 自动", CallMode.GROUP to "🔁 组轮询", CallMode.MANUAL to "🎯 手选")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(MaterialTheme.shapes.small.topStart),
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (m, label) ->
            val selected = mode == m
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onModeChange(m) },
                shape = MaterialTheme.shapes.extraSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 调度状态

@Composable
private fun SchedCard(vm: HomeViewModel) {
    val config by vm.poolConfig.collectAsState()
    val nodes by vm.nodes.collectAsState()

    AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        when (config.mode) {
            CallMode.MANUAL -> {
                val manual = vm.manualNode()
                SchedHeader("🎯 手选", manual?.label?.ifBlank { shortUrl(manual.baseUrl, 20) } ?: "未选择节点")
                Spacer(Modifier.height(6.dp))
                if (vm.manualStopped()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "🛑 连续失败 3 次，已停止调用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = vm::resetManualFailCount) {
                            Text("重置", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Text(
                        "失败计数 ${config.manualFailCount} / ${HomeViewModel.MANUAL_FAIL_LIMIT}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CallMode.GROUP -> {
                SchedHeader("🔁 组轮询", "本次 → 下一个组内节点")
                Spacer(Modifier.height(6.dp))
                Text(
                    "组成员 ${config.groupIds.size} · 游标 #${config.groupCursor} · 成功后前进",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CallMode.AUTO -> {
                val cooling = nodes.filter { it.isCoolingDown() }
                SchedHeader("⚡ 自动", "候选 ${vm.activeCandidateCount()} 个 · 失败自动切换")
                Spacer(Modifier.height(6.dp))
                Text(
                    "排序：健康 → 最近测试 · 连续失败 3 次冷却 10 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cooling.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val n = cooling.first()
                    val remainSec = ((n.cooldownUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    Text(
                        "🧊 ${n.label.ifBlank { shortUrl(n.baseUrl, 18) }} 冷却中（${remainSec / 60}:${"%02d".format(remainSec % 60)} 后恢复）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SchedHeader(tag: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- 日志简版

@Composable
private fun RecentLogCard(vm: HomeViewModel, onOpenLogs: () -> Unit) {
    val logs by vm.recentLogs.collectAsState()

    AppCard {
        SectionHeader("最近调用") {
            TextButton(onClick = onOpenLogs) {
                Text("查看全部 ›", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (logs.isEmpty()) {
            Text(
                "暂无记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            logs.forEach { log ->
                val mark = if (log.success) "✓ ${formatLatency(log.latencyMs)}"
                else "✗ ${log.error?.substringBefore(':') ?: "失败"}"
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        com.example.ttsaggregator.ui.util.formatClock(log.time, "HH:mm"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        shortUrl(log.nodeUrl, 20),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        mark,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (log.success) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
