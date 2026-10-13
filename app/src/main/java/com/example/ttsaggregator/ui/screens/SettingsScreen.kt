package com.example.ttsaggregator.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.NavRow
import com.example.ttsaggregator.ui.components.SectionHeader

// ---------------------------------------------------------------- 设置主页

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember { context.appVersion() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NavRow(
                title = "权限管理",
                subtitle = "电池优化白名单、后台运行等保活权限",
                onClick = onOpenPermissions,
            )
            NavRow(
                title = "节点管理",
                subtitle = "添加/搜索/分页/多选批量/批量测试",
                onClick = onOpenAdmin,
            )
            NavRow(
                title = "关于",
                subtitle = "应用简介 · 基于 zuoban/tts · 开源许可",
                onClick = onOpenAbout,
            )
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("版本 $version", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 权限管理子页面

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    // 每次进入页面重新查询状态
    var batteryIgnored by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "系统 TTS 引擎需要常驻后台。若朗读偶尔中断或被杀，请授予以下权限（国产 ROM 尤其需要）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 1. 电池优化白名单
            PermissionCard(
                title = "电池优化白名单",
                status = if (batteryIgnored) "✅ 已忽略电池优化" else "⚠️ 未忽略，可能被系统杀后台",
                statusWarn = !batteryIgnored,
                actionText = if (batteryIgnored) "重新检查" else "去授权",
            ) {
                if (!batteryIgnored) {
                    requestIgnoreBatteryOptimizations(context, onToast)
                }
                batteryIgnored = isIgnoringBatteryOptimizations(context)
            }

            // 2. 后台运行 / 自启动（应用详情页，国产 ROM 各有入口）
            PermissionCard(
                title = "后台运行 / 自启动",
                status = "各 ROM 入口不同，请在系统设置中允许本应用后台活动",
                statusWarn = false,
                actionText = "打开应用设置",
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }.onFailure {
                    onToast("无法打开系统设置：${it.message}")
                }
            }

            // 3. 说明
            AppCard {
                SectionHeader("说明")
                Spacer(Modifier.height(4.dp))
                Text(
                    "· 本引擎通过网络合成语音，需要后台网络权限（已默认授予）\n" +
                        "· 电池白名单可避免朗读中服务被系统回收\n" +
                        "· 保活权限只影响「系统 TTS」路径，App 内朗读不受影响",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    status: String,
    statusWarn: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (statusWarn) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onAction) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------------------------------------------------------- 工具

internal fun Context.appVersion(): String = runCatching {
    @Suppress("DEPRECATION")
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrDefault("1.0")

private fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    pm.isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(false)

private fun requestIgnoreBatteryOptimizations(context: Context, onToast: (String) -> Unit) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }.onFailure {
        onToast("无法申请白名单：${it.message}")
    }
}
