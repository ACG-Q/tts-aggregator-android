package com.example.ttsaggregator.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.SectionHeader
import com.example.ttsaggregator.ui.screens.picker.VoicePickerDialog
import com.example.ttsaggregator.ui.util.voiceDisplayName
import com.example.ttsaggregator.viewmodel.VoiceViewModel

/**
 * 语音设置（首页子页）：声音四级选择（语言▸地区▸声音▸风格）+ 语速/音调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val vm: VoiceViewModel = appViewModel { VoiceViewModel(it.voiceRepository, it.settings) }
    val settings by vm.synthSettings.collectAsState()
    val voices by vm.voiceList.collectAsState()
    val loading by vm.loading.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音设置") },
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
            // ---- 声音 ----
            AppCard {
                SectionHeader("声音", hint = "语言 ▸ 地区 ▸ 声音 ▸ 风格")
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "🎭 声音选择",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "四级级联 · 标签中文化",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                        Text(
                            voiceDisplayName(voices, settings.voice),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("打开声音选择器 ›", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "· 声音/风格仅作用于系统 TTS，HTTP 朗读按节点默认\n· 调用方 App 指定了声音时，以调用方为准",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 语速 / 音调 ----
            AppCard {
                SectionHeader("语速 / 音调", hint = "朗读与系统 TTS 都生效")
                Spacer(Modifier.height(4.dp))

                Text(
                    "语速 ${settings.rate}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.rate.toFloat(),
                    onValueChange = { vm.updateSettings { s -> s.copy(rate = it.toInt()) } },
                    valueRange = -100f..100f,
                )
                Text(
                    "音调 ${settings.pitch}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.pitch.toFloat(),
                    onValueChange = { vm.updateSettings { s -> s.copy(pitch = it.toInt()) } },
                    valueRange = -100f..100f,
                )
                Text(
                    "· 系统 TTS 会叠加调用方 App 的缩放（基准 ± 缩放）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 刷新音色列表入口
            TextButton(
                onClick = { vm.loadVoices(force = true) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.width(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("重新拉取音色列表（当前 ${voices.size} 个）", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showPicker) {
        VoicePickerDialog(vm, onDismiss = { showPicker = false }, onApplied = { onToast("已选择：$it") })
    }
}
