package com.example.ttsaggregator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.ApiBadge
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.HealthBadge
import com.example.ttsaggregator.ui.components.KeyBadge
import com.example.ttsaggregator.ui.theme.StatusColors
import com.example.ttsaggregator.ui.util.shortUrl
import com.example.ttsaggregator.viewmodel.NodesViewModel

/**
 * 节点列表（调用逻辑的子页）：模式感知的节点选择与调度配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val vm: NodesViewModel = appViewModel { NodesViewModel(it.nodeRepository, it.settings) }
    val nodes by vm.nodes.collectAsState()
    val config by vm.poolConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点列表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAdmin) {
                        Icon(Icons.Default.Build, contentDescription = "管理")
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (config.mode) {
                            CallMode.AUTO -> "顺序 = 调度顺序 · 开关 = 参与调度"
                            CallMode.GROUP -> "勾选加入轮询组 · 序号 = 轮换顺序"
                            CallMode.MANUAL -> "单击选择手选节点"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenAdmin) {
                        Text("管理（搜索/批量）›", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            items(nodes, key = { it.id }) { node ->
                ModeAwareNodeRow(
                    node = node,
                    mode = config.mode,
                    inGroup = node.id in config.groupIds,
                    isManual = node.id == config.manualId,
                    onToggleGroup = { vm.toggleGroupMember(node.id) },
                    onSelectManual = { vm.setManual(node.id) },
                    onToggleDisabled = { vm.toggleDisabled(node.id) },
                )
            }

            item {
                Text(
                    "此列表随调用逻辑变化：自动=顺序与开关 · 组=勾选成员 · 手选=单击指定",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeAwareNodeRow(
    node: TtsNode,
    mode: CallMode,
    inGroup: Boolean,
    isManual: Boolean,
    onToggleGroup: () -> Unit,
    onSelectManual: () -> Unit,
    onToggleDisabled: () -> Unit,
) {
    val dimmed = node.disabled || node.isCoolingDown()
    AppCard(
        onClick = if (mode == CallMode.GROUP || mode == CallMode.MANUAL) {
            {
                when (mode) {
                    CallMode.GROUP -> onToggleGroup()
                    CallMode.MANUAL -> onSelectManual()
                    else -> Unit
                }
            }
        } else null,
        containerColor = if (dimmed) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 模式感知控件
            when (mode) {
                CallMode.GROUP -> Checkbox(checked = inGroup, onCheckedChange = { onToggleGroup() })
                CallMode.MANUAL -> RadioButton(selected = isManual, onClick = onSelectManual)
                CallMode.AUTO -> {
                    Text(
                        text = "${node.failCount}×".takeIf { node.failCount > 0 } ?: "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        node.label.ifBlank { shortUrl(node.baseUrl, 22) },
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    if (mode == CallMode.AUTO && node.isCoolingDown()) {
                        Spacer(Modifier.width(6.dp))
                        val remain = (node.cooldownUntil - System.currentTimeMillis()) / 1000
                        Text(
                            "🧊 ${remain / 60}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusColors.Warn,
                        )
                    }
                }
                Text(
                    node.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ApiBadge(node.apiVersion)
                    HealthBadge(node.health)
                }
                Spacer(Modifier.width(4.dp))
                KeyBadge(node.keyless)
            }

            if (mode == CallMode.AUTO) {
                Spacer(Modifier.width(6.dp))
                Switch(checked = !node.disabled, onCheckedChange = { onToggleDisabled() })
            }
        }
    }
}
