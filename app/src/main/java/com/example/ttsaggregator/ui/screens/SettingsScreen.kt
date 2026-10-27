package com.example.ttsaggregator.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.ui.components.PulseIconTile
import com.example.ttsaggregator.ui.components.PulseSubHeader
import com.example.ttsaggregator.ui.components.SettingRow

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAbout: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val version = remember { context.appVersion() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "设置",
                subtitle = "朗读、系统引擎与保活",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                onClick = {
                    runCatching {
                        context.startActivity(Intent("android.speech.tts.ACTION_SETTINGS"))
                    }.onFailure { onToast("无法打开 TTS 设置：${it.message}") }
                },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseIconTile(
                            icon = Icons.Rounded.Headphones,
                            size = 46,
                            background = MaterialTheme.colorScheme.primaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "系统 TTS 引擎",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "把 TTS Aggregator 设为默认朗读引擎",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "当前已作为系统引擎注册。打开系统设置，在「文字转语音」中选择本应用即可供其他 App 调用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "打开系统 TTS 设置 ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            SectionLabel("连接与调度")
            SettingRow(
                title = "权限管理",
                subtitle = "电池优化白名单、后台运行等保活权限",
                icon = Icons.Rounded.Lock,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onOpenPermissions,
            )
            SettingRow(
                title = "节点管理",
                subtitle = "添加、测试、搜索与批量维护 TTS 节点",
                icon = Icons.Rounded.Dns,
                onClick = onOpenAdmin,
            )

            SectionLabel("应用")
            SettingRow(
                title = "关于",
                subtitle = "应用简介 · 开源许可 · 项目主页",
                icon = Icons.Rounded.Info,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onOpenAbout,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.BatterySaver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "TTS Aggregator · 版本 $version",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

internal fun Context.appVersion(): String = runCatching {
    @Suppress("DEPRECATION")
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrDefault("1.0")
