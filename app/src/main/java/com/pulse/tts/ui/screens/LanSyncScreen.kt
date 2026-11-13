package com.pulse.tts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.lansync.LanSyncEntry
import com.pulse.tts.ui.LocalAppContainer
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.components.SettingGroup
import com.pulse.tts.ui.components.SettingRow
import com.pulse.tts.viewmodel.LanSyncPhase
import com.pulse.tts.viewmodel.LanSyncViewModel

@Composable
fun LanSyncScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val appSettings by container.settings.appSettings.collectAsState()
    val vm: LanSyncViewModel = appViewModel {
        LanSyncViewModel(
            context = it.appContext,
            repo = it.nodeRepository,
            autoAllow = { it.settings.appSettings.value.lanSyncAutoAllow },
        )
    }
    val state by vm.state.collectAsState()
    val peers by vm.peers.collectAsState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.start() }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    LaunchedEffect(Unit) {
        LanSyncEntry.pairing.collect { raw ->
            if (raw != null) {
                input = raw
                vm.syncFromString(raw)
                vm.consumeDeepLink()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(title = "局域网同步", subtitle = "与电脑双向合并节点", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "同一 Wi-Fi 下会自动发现电脑。也可以让电脑扫描或粘贴下方的配对串。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingGroup {
                SettingRow(
                    title = "自动允许同步请求",
                    subtitle = "开启后不再弹窗确认，仅建议在可信任的网络中使用。",
                    icon = Icons.Rounded.VerifiedUser,
                    onClick = {
                        container.settings.updateAppSettings {
                            it.copy(lanSyncAutoAllow = !it.lanSyncAutoAllow)
                        }
                    },
                    trailing = {
                        Switch(
                            checked = appSettings.lanSyncAutoAllow,
                            onCheckedChange = { value ->
                                container.settings.updateAppSettings {
                                    it.copy(lanSyncAutoAllow = value)
                                }
                            },
                        )
                    },
                )
            }
            Spacer(Modifier.height(12.dp))

            SettingGroup {
                Column(Modifier.padding(14.dp)) {
                    Text("本机配对串", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.pairing.ifBlank { "正在启动…" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.localIp.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "地址 ${state.localIp}:${state.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("附近的电脑", style = MaterialTheme.typography.titleSmall)
            if (peers.isEmpty()) {
                Text(
                    "暂未发现设备。确认电脑已开启局域网同步，并与手机处于同一 Wi-Fi。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(peers, key = { it.sid }) { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.syncTo(peer) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(peer.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (peer.platform == "desktop") "电脑" else "手机",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("同步", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("粘贴配对串") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.syncFromString(input) },
                enabled = input.isNotBlank() && state.phase != LanSyncPhase.SYNCING,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Text(if (state.phase == LanSyncPhase.SYNCING) "同步中…" else "连接")
            }

            state.stats?.let { stats ->
                Text(
                    "同步完成：新增 ${stats.added}，更新 ${stats.updated}，跳过 ${stats.skipped}。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.phase == LanSyncPhase.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.reject() },
            icon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
            title = { Text("${pending.fromName} 请求同步") },
            text = { Text("将发送 ${pending.nodes.size} 个节点，并接收本机节点。本机的密钥、禁用状态与分组不会被改动。") },
            confirmButton = { TextButton(onClick = { vm.accept() }) { Text("确认同步") } },
            dismissButton = { TextButton(onClick = { vm.reject() }) { Text("拒绝") } },
        )
    }
}
