package com.example.ttsaggregator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ttsaggregator.data.ApiVersion
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.data.parseNodeImport
import com.example.ttsaggregator.ui.theme.MonoTextStyle
import com.example.ttsaggregator.ui.util.NodeState
import com.example.ttsaggregator.ui.util.badgeLabel
import com.example.ttsaggregator.ui.util.relativeTimeLabel
import com.example.ttsaggregator.ui.util.statusLabel
import com.example.ttsaggregator.ui.util.uiState
import com.example.ttsaggregator.ui.util.validateNodeDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailSheet(
    node: TtsNode,
    mode: CallMode,
    isPrimary: Boolean,
    inGroup: Boolean,
    isManual: Boolean,
    latencyMs: Long?,
    now: Long,
    testing: Boolean,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onToggleDisabled: () -> Unit,
    onToggleGroup: () -> Unit,
    onSetManual: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = node.uiState(now)
    val status = if (state == NodeState.AVAILABLE && latencyMs != null) {
        "可用 · $latencyMs ms"
    } else {
        node.statusLabel(now)
    }
    val badge = node.badgeLabel(now, isPrimary = isPrimary, isGroupMember = inGroup)
    val auth = if (node.keyless || node.apiKey.isNullOrBlank()) "无鉴权" else "Bearer · 已保存"
    val version = when (node.apiVersion) {
        ApiVersion.V1 -> "新版 V1"
        ApiVersion.LEGACY -> "旧版兼容"
        ApiVersion.UNKNOWN -> "未识别"
    }
    val capability = if (node.voiceCount > 0) "$version · ${node.voiceCount} 音色" else version

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                "节点详情",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "健康度决定自动调度的顺序",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HealthTile(state = state, size = 44, corner = 13, iconSize = 22)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        node.label.ifBlank { node.baseUrl },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                NodeBadge(badge)
            }

            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    DetailRow("接口地址", node.baseUrl, mono = true)
                    DetailDivider()
                    DetailRow("分组", node.group)
                    DetailDivider()
                    DetailRow("鉴权", auth)
                    DetailDivider()
                    DetailRow("上次测试", relativeTimeLabel(node.lastChecked, now))
                    DetailDivider()
                    DetailRow(
                        "连续失败",
                        "${node.failCount} 次${if (node.isCoolingDown(now)) " · 冷却中" else ""}",
                    )
                    DetailDivider()
                    DetailRow("接口能力", capability)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailAction(
                    label = if (testing) "测试中…" else "测试连接",
                    icon = Icons.Rounded.Refresh,
                    loading = testing,
                    onClick = onTest,
                    modifier = Modifier.weight(1f),
                )
                DetailAction(
                    label = if (node.disabled) "启用节点" else "停用节点",
                    icon = if (node.disabled) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    onClick = onToggleDisabled,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailAction(
                    label = "编辑",
                    icon = Icons.Rounded.Edit,
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                )
                DetailAction(
                    label = "删除",
                    icon = Icons.Rounded.Delete,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }

            if (mode != CallMode.AUTO) {
                Spacer(Modifier.height(8.dp))
                val label = when {
                    mode == CallMode.GROUP && inGroup -> "移出轮询"
                    mode == CallMode.GROUP -> "加入轮询"
                    isManual -> "已是当前节点"
                    else -> "设为当前节点"
                }
                DetailAction(
                    label = label,
                    icon = Icons.Rounded.Check,
                    enabled = !(mode == CallMode.GROUP && inGroup) &&
                        !(mode == CallMode.MANUAL && isManual),
                    onClick = {
                        if (mode == CallMode.GROUP) onToggleGroup() else onSetManual()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNodeSheet(
    adding: Boolean,
    groups: List<String>,
    initialGroup: String = "默认",
    onDismiss: () -> Unit,
    onSave: (url: String, label: String, apiKey: String?, group: String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(initialGroup) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun submit() {
        val trimmedLabel = label.trim()
        val trimmedUrl = url.trim()
        val validation = validateNodeDraft(trimmedLabel, trimmedUrl)
        if (validation != null) {
            error = validation
        } else {
            error = null
            onSave(trimmedUrl, trimmedLabel, apiKey.trim().ifEmpty { null }, group)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                "添加节点",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "保存后会测试一次连接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "密钥只保存在本机。保存后会测试一次连接。",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel("名称")
            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("例如：东京 · 主节点", style = MaterialTheme.typography.bodySmall)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
                enabled = !adding,
            )

            Spacer(Modifier.height(14.dp))
            FieldLabel("接口地址")
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("https://tts.example.com/v1", style = MaterialTheme.typography.bodySmall)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
                enabled = !adding,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(5.dp))
            HelperText("支持 OpenAI 兼容的 /audio/speech 接口")

            Spacer(Modifier.height(14.dp))
            FieldLabel("API Key")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
                enabled = !adding,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(5.dp))
            HelperText("留空则使用无鉴权请求")

            Spacer(Modifier.height(14.dp))
            FieldLabel("分组")
            GroupSelector(
                groups = groups,
                selected = group,
                enabled = !adding,
                onSelect = { group = it },
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !adding,
                shape = RoundedCornerShape(14.dp),
            ) {
                if (adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在测试连接…")
                } else {
                    Text("保存并测试")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeSheet(
    node: TtsNode,
    groups: List<String>,
    onDismiss: () -> Unit,
    onSave: (label: String, apiKey: String?, group: String) -> Unit,
) {
    var label by remember { mutableStateOf(node.label) }
    var apiKey by remember { mutableStateOf(node.apiKey.orEmpty()) }
    var group by remember { mutableStateOf(node.group) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                "编辑节点",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                node.baseUrl,
                style = MonoTextStyle.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            FieldLabel("名称")
            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(14.dp))
            FieldLabel("API Key")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(5.dp))
            HelperText(
                if (node.apiKey.isNullOrBlank()) "留空则使用无鉴权请求" else "清空将删除已保存的 Key",
            )

            Spacer(Modifier.height(14.dp))
            FieldLabel("分组")
            GroupSelector(
                groups = groups,
                selected = group,
                enabled = true,
                onSelect = { group = it },
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (label.isBlank()) error = "给节点起个名字"
                    else onSave(label.trim(), apiKey.trim().ifEmpty { null }, group)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("保存修改")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAddSheet(
    adding: Boolean,
    groups: List<String>,
    initialGroup: String = "默认",
    onDismiss: () -> Unit,
    onImport: (raw: String, group: String, autoTest: Boolean) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(initialGroup) }
    var autoTest by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parsed = remember(raw) { parseNodeImport(raw) }
    val validCount = parsed.size
    val duplicateCount = parsed.size - parsed.map { it.url }.distinct().size
    val invalidCount = if (raw.trimStart().startsWith("{")) {
        0
    } else {
        (raw.lines().count { it.isNotBlank() } - validCount).coerceAtLeast(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                "批量添加",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "每行一个节点，支持「地址 | 名称 | 分组」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "https://tts.example.com/v1 | 东京 | 默认\n" +
                            "https://backup.example.com/v1",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                textStyle = MonoTextStyle.copy(fontSize = 12.sp, lineHeight = 18.sp),
                minLines = 6,
                maxLines = 10,
                shape = RoundedCornerShape(12.dp),
                enabled = !adding,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "有效 $validCount · 重复 $duplicateCount · 无效 $invalidCount",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (invalidCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            FieldLabel("分组")
            GroupSelector(
                groups = groups,
                selected = group,
                enabled = !adding,
                onSelect = { group = it },
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = autoTest,
                    onCheckedChange = { autoTest = it },
                    enabled = !adding,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "导入后自动测试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onImport(raw, group, autoTest) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = validCount > 0 && !adding,
                shape = RoundedCornerShape(14.dp),
            ) {
                if (adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在导入…")
                } else {
                    Text(if (autoTest) "导入并测试" else "导入节点")
                }
            }
        }
    }
}

@Composable
internal fun GroupSelector(
    groups: List<String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { group ->
            val isSelected = group == selected
            Surface(
                onClick = { onSelect(group) },
                enabled = enabled,
                shape = RoundedCornerShape(9.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Text(
                    group,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = if (mono) {
                MonoTextStyle.copy(fontSize = 10.sp, lineHeight = 14.sp)
            } else {
                MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
            },
            fontWeight = if (mono) FontWeight.Normal else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f, fill = false),
        )
    }
}

@Composable
private fun DetailDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun DetailAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun HelperText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
