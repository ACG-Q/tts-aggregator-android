package com.pulse.tts.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.exportNodesJson
import com.pulse.tts.data.normalizeGithubProxy
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.AppTextField
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.components.SettingConfirmSheet
import com.pulse.tts.ui.components.SettingDivider
import com.pulse.tts.ui.components.SettingSection
import com.pulse.tts.ui.components.SettingValueRow
import com.pulse.tts.ui.util.shortUrl
import com.pulse.tts.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * 更多设置：承接从首页下沉的低频项。
 *
 * 分组为 服务 / 数据 / 下载 / 关于，只做搬运，业务逻辑与首页原先一致。
 */
@Composable
fun MoreSettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = appViewModel {
        SettingsViewModel(it.nodeRepository, it.settings, it.logStore, it.voiceRepository, it.audioCache)
    }
    val appSettings by vm.appSettings.collectAsState()
    val nodes by vm.nodes.collectAsState()
    val logCount by vm.logCount.collectAsState()
    val importing by vm.importing.collectAsState()
    val version = remember { context.appVersion() }
    val scope = rememberCoroutineScope()

    var showPrivacy by remember { mutableStateOf(false) }
    var showClearLogs by remember { mutableStateOf(false) }
    var showProxyInput by remember { mutableStateOf(false) }
    var proxyDraft by remember { mutableStateOf("") }
    var proxyError by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    onToast("文件为空或无法读取")
                } else {
                    vm.importNodes(raw, onToast)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val json = exportNodesJson(nodes, appSettings, Instant.now().toString())
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val out = context.contentResolver.openOutputStream(uri)
                            ?: error("无法写入文件")
                        out.bufferedWriter().use { it.write(json) }
                    }.isSuccess
                }
                onToast(
                    if (ok) "已导出 ${nodes.size} 个节点（不含密钥）"
                    else "导出失败，请重试",
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "更多设置",
                subtitle = "服务、数据与应用信息",
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
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SettingSection("服务") {
                SettingValueRow(
                    icon = Icons.Rounded.Headphones,
                    title = "系统 TTS 引擎",
                    subtitle = "已作为系统朗读引擎注册",
                    value = "已注册",
                    onClick = { openSystemTtsSettings(context, onToast) },
                    onTrailingClick = { openSystemTtsSettings(context, onToast) },
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.Security,
                    title = "隐私",
                    subtitle = "了解文本与密钥的处理方式",
                    onClick = { showPrivacy = true },
                    onTrailingClick = { showPrivacy = true },
                )
            }

            SettingSection("数据") {
                SettingValueRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "导入节点",
                    subtitle = "JSON 含偏好设置，文本清单仅导入节点",
                    value = if (importing) "导入中…" else "",
                    onClick = {
                        if (!importing) importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    onTrailingClick = {
                        if (!importing) importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.FileUpload,
                    title = "导出配置",
                    subtitle = "导出节点与偏好设置，不含密钥",
                    onClick = { exportLauncher.launch("tts-aggregator-config.json") },
                    onTrailingClick = { exportLauncher.launch("tts-aggregator-config.json") },
                )
                SettingDivider()
                SettingValueRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "清空调用日志",
                    subtitle = "删除全部记录，无法恢复",
                    value = "$logCount 条",
                    highlight = true,
                    onClick = { if (logCount > 0) showClearLogs = true else onToast("暂无调用日志") },
                    onTrailingClick = {
                        if (logCount > 0) showClearLogs = true else onToast("暂无调用日志")
                    },
                )
            }

            SettingSection("下载") {
                SettingValueRow(
                    icon = Icons.Rounded.Cloud,
                    title = "GitHub 加速地址",
                    subtitle = "仅用于检查更新与下载 APK，留空直连",
                    value = appSettings.githubProxy
                        .takeIf { it.isNotBlank() }
                        ?.let { shortUrl(it, 22) }
                        ?: "直连",
                    onClick = {
                        proxyDraft = appSettings.githubProxy
                        proxyError = null
                        showProxyInput = true
                    },
                    onTrailingClick = {
                        proxyDraft = appSettings.githubProxy
                        proxyError = null
                        showProxyInput = true
                    },
                )
            }

            SettingSection("关于") {
                SettingValueRow(
                    icon = Icons.Rounded.Info,
                    title = "关于 Pulse",
                    subtitle = "版本 $version · 开源许可",
                    onClick = onOpenAbout,
                    onTrailingClick = onOpenAbout,
                )
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私说明") },
            text = {
                Text(
                    "文本仅在本机与所选节点之间传输，不做云端中转。\n\n" +
                        "导出的配置文件不包含节点密钥；导入时也不会读取或还原密钥。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("知道了") }
            },
        )
    }

    if (showClearLogs) {
        SettingConfirmSheet(
            title = "清空调用日志？",
            message = "将删除全部 $logCount 条记录，此操作不可撤销。",
            confirmLabel = "清空",
            destructive = true,
            onDismiss = { showClearLogs = false },
            onConfirm = {
                vm.clearLogs()
                showClearLogs = false
                onToast("调用日志已清空")
            },
        )
    }

    if (showProxyInput) {
        AlertDialog(
            onDismissRequest = { showProxyInput = false },
            title = { Text("GitHub 加速地址") },
            text = {
                Column {
                    AppTextField(
                        value = proxyDraft,
                        onValueChange = {
                            proxyDraft = it
                            proxyError = null
                        },
                        placeholder = "https://ghproxy.net/",
                        isError = proxyError != null,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "填写加速服务前缀，例如 https://ghproxy.net/。留空表示直连。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    proxyError?.let { error ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = normalizeGithubProxy(proxyDraft)
                        if (normalized == null) {
                            proxyError = "地址必须以 http:// 或 https:// 开头"
                        } else {
                            vm.updateApp { it.copy(githubProxy = normalized) }
                            showProxyInput = false
                            onToast(if (normalized.isBlank()) "已改为直连" else "加速地址已保存")
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showProxyInput = false }) { Text("取消") }
            },
        )
    }
}
