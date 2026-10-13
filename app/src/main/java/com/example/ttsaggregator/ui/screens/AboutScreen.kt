package com.example.ttsaggregator.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.SectionHeader

/**
 * 关于：应用定位说明 + 开源项目链接
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val version = remember { context.appVersion() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
            // 标题
            Text("TTS Aggregator", style = MaterialTheme.typography.displaySmall)
            Text(
                "版本 $version",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 简介
            AppCard {
                SectionHeader("📖 这是一款「TTS 中转站聚合」应用")
                Spacer(Modifier.height(4.dp))
                Text(
                    "基于开源项目 zuoban/tts 搭建的服务生态，将多个独立部署的 " +
                        "TTS 中转节点统一接入，聚合为一个系统级文本转语音引擎。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 功能
            AppCard {
                SectionHeader("功能")
                Spacer(Modifier.height(4.dp))
                Text(
                    "· 新老两代 zuoban/tts API 自动识别（新版 V1 / 老版兼容）\n" +
                        "· 节点池智能容灾：自动切换 · 组轮询 · 手选连败停止\n" +
                        "· SQLite 存储，支持万级节点的搜索与批量管理\n" +
                        "· 完整调用日志：耗时 · 音色 · 文本 · 切换链路",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 项目主页
            AppCard(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zuoban/tts")),
                        )
                    }.onFailure { onToast("无法打开浏览器：${it.message}") }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔗", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "项目主页（zuoban/tts）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "github.com/zuoban/tts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // 许可与声明
            AppCard {
                SectionHeader("开源许可：MIT License")
                Spacer(Modifier.height(4.dp))
                Text(
                    "本应用为社区客户端，基于 zuoban/tts 的公开 API 构建，" +
                        "与 zuoban/tts 官方无关联。部署属于你自己的中转节点即可接入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
