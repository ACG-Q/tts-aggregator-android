package com.pulse.tts.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.pulse.tts.data.UpdateClient
import com.pulse.tts.data.UpdateInfo
import com.pulse.tts.ui.util.ApkInstaller
import com.pulse.tts.viewmodel.UpdateViewModel
import kotlin.math.roundToInt

/**
 * 全局更新弹窗宿主：挂在 [com.pulse.tts.navigation.AppNav] 上，
 * 设置页与关于页只负责触发检查，结果统一在这里呈现。
 */
@Composable
fun UpdateHost(
    vm: UpdateViewModel,
    onToast: (String) -> Unit,
    onExitApp: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { onToast(it) }
    }

    LaunchedEffect(state.downloadFailedPage) {
        val page = state.downloadFailedPage ?: return@LaunchedEffect
        ApkInstaller.openReleasePage(context, page)
        vm.clearDownloadFailure()
    }

    state.optional?.let { info ->
        UpdateAvailableDialog(
            info = info,
            forced = false,
            onDismiss = vm::dismissOptional,
            onUpdate = { vm.downloadAndInstall(context) },
            onExitApp = onExitApp,
        )
    }

    state.forced?.let { info ->
        UpdateAvailableDialog(
            info = info,
            forced = true,
            onDismiss = {},
            onUpdate = { vm.downloadAndInstall(context) },
            onExitApp = onExitApp,
        )
    }

    if (state.downloading) {
        DownloadProgressDialog(
            progress = state.progress,
            onCancel = vm::cancelDownload,
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: UpdateInfo,
    forced: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onExitApp: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !forced,
            dismissOnClickOutside = !forced,
        ),
        title = {
            Text(if (forced) "需要更新" else "发现新版本 v${info.versionName}")
        },
        text = {
            Column {
                if (forced) {
                    Text(
                        "当前版本与 TTS 服务端不再兼容，请更新后继续使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "版本 v${info.versionName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (info.versionName.contains('-')) {
                        Spacer(Modifier.width(6.dp))
                        PrereleaseBadge()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    info.notes.ifBlank { "本次更新包含功能改进与问题修复。" },
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!forced) {
                    TextButton(
                        onClick = {
                            ApkInstaller.openReleasePage(context, UpdateClient.ALL_RELEASES_PAGE)
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text("查看全部版本", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("立即更新") }
        },
        dismissButton = {
            if (forced) {
                TextButton(onClick = onExitApp) { Text("退出应用") }
            } else {
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        },
    )
}

@Composable
private fun PrereleaseBadge() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            "测试版",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DownloadProgressDialog(
    progress: Float?,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text("正在下载更新") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (progress != null) {
                        "已下载 ${(progress * 100).roundToInt()}%"
                    } else {
                        "正在连接下载服务器…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}
