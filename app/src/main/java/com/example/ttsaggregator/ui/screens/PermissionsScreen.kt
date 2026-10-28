package com.example.ttsaggregator.ui.screens

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.PulseIconTile
import com.example.ttsaggregator.ui.components.PulseSubHeader
import com.example.ttsaggregator.ui.permissions.PermissionKey
import com.example.ttsaggregator.ui.permissions.PermissionState
import com.example.ttsaggregator.ui.permissions.permissionBadgeText
import com.example.ttsaggregator.ui.theme.StatusColors

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    var states by remember { mutableStateOf(readPermissionStates(context)) }

    fun refresh() {
        states = readPermissionStates(context)
    }

    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh() }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        refresh()
        onToast(if (uri != null) "已获得文件访问权限" else "未选择文件")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "权限",
                subtitle = "让朗读在后台活下去",
                onBack = onBack,
                actions = {
                    TextButton(onClick = {
                        val next = readPermissionStates(context)
                        states = next
                        onToast(permissionBadgeText(next))
                    }) {
                        Text("一键检查", style = MaterialTheme.typography.labelLarge)
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "系统 TTS 引擎需要常驻后台。若朗读偶尔中断或被杀，请授予以下权限（国产 ROM 尤其需要）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        permissionBadgeText(states),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            states.forEach { state ->
                PermissionCard(
                    state = state,
                    onAction = {
                        when (state.key) {
                            PermissionKey.NOTIFY -> {
                                if (state.granted) {
                                    openNotificationSettings(context, onToast)
                                } else if (Build.VERSION.SDK_INT >= 33) {
                                    notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openNotificationSettings(context, onToast)
                                }
                            }
                            PermissionKey.BATTERY -> {
                                if (state.granted) {
                                    refresh()
                                    onToast("电池优化状态已刷新")
                                } else {
                                    batteryLauncher.launch(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                            }
                            PermissionKey.BACKGROUND,
                            PermissionKey.NETWORK,
                            -> openAppDetails(context, onToast)
                            PermissionKey.TTS -> openTtsSettings(context, onToast)
                            PermissionKey.FILES -> fileLauncher.launch(arrayOf("*/*"))
                        }
                    },
                )
            }

            AppCard {
                Text(
                    "为什么需要这些权限",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 通知权限用于播放状态与完成提醒\n" +
                        "· 电池白名单可避免朗读中服务被系统回收\n" +
                        "· 后台活动与数据权限影响系统 TTS 持续联网\n" +
                        "· 节点合成只在本机与所选节点之间传输",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    state: PermissionState,
    onAction: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseIconTile(
                icon = state.key.icon(),
                tint = if (state.granted) StatusColors.Ok else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.key.title(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = (if (state.granted) StatusColors.Ok
                    else MaterialTheme.colorScheme.error).copy(alpha = 0.12f),
                ) {
                    Text(
                        state.statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.granted) StatusColors.Ok
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Icon(
                imageVector = state.key.icon(),
                contentDescription = null,
                tint = if (state.granted) StatusColors.Ok else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            state.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onAction) {
                Text(state.key.actionText(state.granted), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun PermissionKey.title(): String = when (this) {
    PermissionKey.NOTIFY -> "通知"
    PermissionKey.BATTERY -> "电池优化白名单"
    PermissionKey.BACKGROUND -> "后台运行 / 自启动"
    PermissionKey.NETWORK -> "后台网络"
    PermissionKey.TTS -> "系统 TTS 默认引擎"
    PermissionKey.FILES -> "导入导出文件"
}

private fun PermissionKey.actionText(granted: Boolean): String = when (this) {
    PermissionKey.NOTIFY -> if (granted) "通知设置" else "授权通知"
    PermissionKey.BATTERY -> if (granted) "重新检查" else "去授权"
    PermissionKey.BACKGROUND -> "打开应用设置"
    PermissionKey.NETWORK -> "打开应用设置"
    PermissionKey.TTS -> "打开 TTS 设置"
    PermissionKey.FILES -> "验证文件访问"
}

private fun PermissionKey.icon(): ImageVector = when (this) {
    PermissionKey.NOTIFY -> Icons.Rounded.Notifications
    PermissionKey.BATTERY -> Icons.Rounded.BatterySaver
    PermissionKey.BACKGROUND -> Icons.Rounded.Autorenew
    PermissionKey.NETWORK -> Icons.Rounded.Wifi
    PermissionKey.TTS -> Icons.Rounded.RecordVoiceOver
    PermissionKey.FILES -> Icons.Rounded.Folder
}

internal fun readPermissionStates(context: Context): List<PermissionState> {
    val notify = if (Build.VERSION.SDK_INT >= 33) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val battery = runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        power.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
    val background = if (Build.VERSION.SDK_INT >= 28) {
        runCatching {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.isBackgroundRestricted.not()
        }.getOrDefault(true)
    } else {
        true
    }
    val network = runCatching {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }.getOrDefault(true)
    val tts = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.TTS_DEFAULT_SYNTH,
        ) == context.packageName
    }.getOrDefault(false)

    return listOf(
        PermissionState(
            key = PermissionKey.NOTIFY,
            granted = notify,
            statusText = if (notify) "通知已允许" else "通知未授权",
            detail = "后台播放与完成提醒需要通知权限。",
        ),
        PermissionState(
            key = PermissionKey.BATTERY,
            granted = battery,
            statusText = if (battery) "已加入白名单" else "未加入白名单",
            detail = "避免系统在朗读时回收进程。",
        ),
        PermissionState(
            key = PermissionKey.BACKGROUND,
            granted = background,
            statusText = if (background) "允许后台活动" else "后台活动受限",
            detail = "部分系统会限制后台网络与进程。",
        ),
        PermissionState(
            key = PermissionKey.NETWORK,
            granted = network,
            statusText = if (network) "不受数据节省限制" else "后台数据受限",
            detail = "节点合成需要网络。",
        ),
        PermissionState(
            key = PermissionKey.TTS,
            granted = tts,
            statusText = if (tts) "当前为默认引擎" else "未设为默认引擎",
            detail = "其他应用调用系统 TTS 时会使用本应用。",
        ),
        PermissionState(
            key = PermissionKey.FILES,
            granted = true,
            statusText = "按需访问",
            detail = "导入/导出通过系统文件选择器完成，无需存储权限。",
        ),
    )
}

private fun openAppDetails(context: Context, onToast: (String) -> Unit) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }.onFailure { onToast("无法打开系统设置：${it.message}") }
}

private fun openNotificationSettings(context: Context, onToast: (String) -> Unit) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        )
    }.onFailure { onToast("无法打开通知设置：${it.message}") }
}

private fun openTtsSettings(context: Context, onToast: (String) -> Unit) {
    runCatching {
        context.startActivity(Intent("android.speech.tts.ACTION_SETTINGS"))
    }.onFailure { onToast("无法打开 TTS 设置：${it.message}") }
}
