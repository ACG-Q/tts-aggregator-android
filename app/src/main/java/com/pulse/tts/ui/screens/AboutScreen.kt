package com.pulse.tts.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.tts.ui.components.AppCard
import com.pulse.tts.ui.components.PulseBrandMark
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.viewmodel.UpdateViewModel

/**
 * 关于：应用定位、能力清单与开源项目入口。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val version = remember { context.appVersion() }
    val updateVm: UpdateViewModel = appViewModel { UpdateViewModel(it.updateRepository) }
    val updateState by updateVm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "关于",
                subtitle = "Pulse · 聚合朗读",
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
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseBrandMark(size = 64)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pulse",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "版本 $version",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { if (!updateState.checking) updateVm.checkManually() },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text(
                                if (updateState.checking) "检查中…" else "检查更新",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        "把文字交给声音。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            AppCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "一个「TTS 中转站聚合」客户端",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "基于开源项目 zuoban/tts 搭建的服务生态，把多个独立部署的 TTS 节点聚合成一个系统级文本转语音引擎，并在应用内提供完整的朗读体验。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "能力",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Capability("新老两代 API 自动识别，新版 V1 / 老版兼容")
                Capability("节点池容灾：自动切换 · 组轮询 · 手选连败停止")
                Capability("朗读态逐句高亮、进度拖动、倍速与定时")
                Capability("完整调用记录：耗时 · 音色 · 文本 · 切换链路")
            }

            ProjectLinkCard(
                title = "软件项目",
                url = "github.com/acg-q/tts-aggregator-android",
                description = "Pulse 客户端源码 · 问题反馈 · 版本发布",
                onOpen = {
                    openUrl(
                        context,
                        "https://github.com/acg-q/tts-aggregator-android",
                        onToast,
                    )
                },
            )

            ProjectLinkCard(
                title = "TTS 项目",
                url = "github.com/zuoban/tts",
                description = "本应用接入的开源 TTS 中转服务",
                onOpen = { openUrl(context, "https://github.com/zuoban/tts", onToast) },
            )

            Text(
                "MIT License · Pulse 与 zuoban/tts 是相互独立的开源项目。部署自己的中转节点即可接入。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ProjectLinkCard(
    title: String,
    url: String,
    description: String,
    onOpen: () -> Unit,
) {
    AppCard(
        onClick = onOpen,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openUrl(context: Context, url: String, onToast: (String) -> Unit) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure { onToast("无法打开浏览器：${it.message}") }
}

@Composable
private fun Capability(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
