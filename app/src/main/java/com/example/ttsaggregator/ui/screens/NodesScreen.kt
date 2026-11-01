package com.example.ttsaggregator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ttsaggregator.data.ApiVersion
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.data.exportNodesJson
import com.example.ttsaggregator.data.knownGroups
import com.example.ttsaggregator.ui.LocalAppSettings
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.AddNodeSheet
import com.example.ttsaggregator.ui.components.BatchAddSheet
import com.example.ttsaggregator.ui.components.BatchBar
import com.example.ttsaggregator.ui.components.EditNodeSheet
import com.example.ttsaggregator.ui.components.EmptyState
import com.example.ttsaggregator.ui.components.GroupSelectSheet
import com.example.ttsaggregator.ui.components.HealthTile
import com.example.ttsaggregator.ui.components.NodeBadge
import com.example.ttsaggregator.ui.components.NodeDetailSheet
import com.example.ttsaggregator.ui.components.NodeSelectBox
import com.example.ttsaggregator.ui.components.PulseBrandMark
import com.example.ttsaggregator.ui.components.PulseMainHeader
import com.example.ttsaggregator.ui.theme.MonoTextStyle
import com.example.ttsaggregator.ui.theme.StatusColors
import com.example.ttsaggregator.ui.util.NodeState
import com.example.ttsaggregator.ui.util.badgeLabel
import com.example.ttsaggregator.ui.util.nodeSummary
import com.example.ttsaggregator.ui.util.relativeTimeLabel
import com.example.ttsaggregator.ui.util.secondaryLabel
import com.example.ttsaggregator.ui.util.statusLabel
import com.example.ttsaggregator.ui.util.uiState
import com.example.ttsaggregator.ui.util.validateNodeDraft
import com.example.ttsaggregator.viewmodel.NodesViewModel
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * 节点一级页：品牌头部 + 调度概览 + 模式分段器 + 健康卡片。
 * 卡片信息层级对齐 Pulse 原型：健康图标 / 名称与徽章 / 延迟与测试时间 / 地址。
 */
@Composable
fun NodesScreen(
    onToast: (String) -> Unit,
) {
    val vm: NodesViewModel = appViewModel {
        NodesViewModel(it.nodeRepository, it.settings, it.logStore)
    }
    val context = LocalContext.current
    val appSettings = LocalAppSettings.current
    val haptics = LocalHapticFeedback.current
    val nodes by vm.nodes.collectAsState()
    val groups = remember(nodes) { knownGroups(nodes) }
    val config by vm.poolConfig.collectAsState()
    val callLogs by vm.callLogs.collectAsState()
    val adding by vm.adding.collectAsState()
    val importing by vm.importing.collectAsState()
    val busyNodeIds by vm.busyNodeIds.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showBatchAdd by remember { mutableStateOf(false) }
    var showGroupSelect by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var editNodeId by remember { mutableStateOf<String?>(null) }
    var deleteNodeId by remember { mutableStateOf<String?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingExport by remember { mutableStateOf<List<TtsNode>>(emptyList()) }

    fun exitSelection() {
        selectMode = false
        selected = emptySet()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = pendingExport
        pendingExport = emptyList()
        if (uri != null && export.isNotEmpty()) {
            runCatching {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("无法写入文件")
                stream.bufferedWriter().use {
                    it.write(exportNodesJson(export, appSettings, Instant.now().toString()))
                }
            }.onSuccess {
                onToast("已导出 ${export.size} 个节点（不含密钥）")
                exitSelection()
            }.onFailure {
                onToast("导出失败：${it.message ?: "未知错误"}")
            }
        }
    }

    val latencyByUrl = remember(callLogs) {
        buildMap {
            callLogs.forEach { log ->
                if (log.success && log.nodeUrl !in this) put(log.nodeUrl, log.latencyMs)
            }
        }
    }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val summary = nodes.nodeSummary(now)
    val primaryId = remember(nodes, config, now) {
        when (config.mode) {
            CallMode.AUTO -> nodes
                .filter { !it.disabled && it.health != TtsNode.Health.FAIL && !it.isCoolingDown(now) }
                .sortedWith(compareBy({ it.health.ordinal }, { -it.lastChecked }))
                .firstOrNull()?.id
            CallMode.GROUP -> nodes
                .filter { it.id in config.groupIds && !it.disabled && !it.isCoolingDown(now) }
                .let { group ->
                    if (group.isEmpty()) null
                    else group[config.groupCursor % group.size].id
                }
            CallMode.MANUAL -> config.manualId
        }
    }

    val allSelected = nodes.isNotEmpty() && selected.containsAll(nodes.map { it.id })
    val isGroupMode = config.mode == CallMode.GROUP

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            NodesHeader(
                count = nodes.size,
                selectMode = selectMode,
                onSelect = {
                    if (selectMode) exitSelection() else selectMode = true
                },
                onAdd = { showAdd = true },
                onLongAdd = {
                    if (appSettings.haptics) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    showBatchAdd = true
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 2.dp,
                    bottom = if (selectMode) 260.dp else 96.dp,
                ),
            ) {
                item {
                    Hero(config.mode)
                }
                item {
                    SummaryRow(summary.available, summary.cooling, summary.disabled)
                }
                item {
                    ModeSegmented(
                        mode = config.mode,
                        onMode = vm::setMode,
                        modifier = Modifier.padding(top = 16.dp, bottom = 14.dp),
                    )
                }
                if (isGroupMode) {
                    item {
                        GroupPoolPanel(
                            nodes = nodes,
                            groupIds = config.groupIds,
                            onSelect = { showGroupSelect = true },
                            onSelectAll = {
                                vm.setGroupMembers(
                                    nodes.filter { !it.disabled }.map { it.id }.toSet(),
                                )
                            },
                        )
                    }
                }

                if (nodes.isEmpty()) {
                    item {
                        EmptyState(
                            "还没有节点。点右上角 + 添加第一个 TTS 接口。",
                            Modifier.padding(bottom = 4.dp),
                        )
                    }
                } else {
                    items(nodes, key = { it.id }) { node ->
                        val badge = node.badgeLabel(
                            now,
                            isPrimary = node.id == primaryId && !node.disabled,
                            isGroupMember = node.id in config.groupIds,
                        )
                        fun toggleCheck() {
                            selected = if (node.id in selected) selected - node.id
                            else selected + node.id
                        }
                        NodeCard(
                            node = node,
                            isPrimary = node.id == primaryId && !node.disabled,
                            badge = badge,
                            latencyMs = latencyByUrl[node.baseUrl],
                            now = now,
                            selectMode = selectMode,
                            checked = node.id in selected,
                            showGroupToggle = isGroupMode && !selectMode,
                            inGroup = node.id in config.groupIds,
                            onClick = {
                                if (selectMode) toggleCheck() else detailId = node.id
                            },
                            onToggleCheck = { toggleCheck() },
                            onToggleGroup = { vm.toggleGroupMember(node.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                item {
                    Spacer(Modifier.height(2.dp))
                    AddNodeButton(
                        onClick = { showAdd = true },
                        onLongClick = {
                            if (appSettings.haptics) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            showBatchAdd = true
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    HintRow()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (selectMode) {
            BatchBar(
                selectedCount = selected.size,
                allSelected = allSelected,
                showGroupAction = isGroupMode,
                showExportAction = true,
                onSelectAll = {
                    selected = if (allSelected) emptySet() else nodes.map { it.id }.toSet()
                },
                onInvert = { selected = nodes.map { it.id }.toSet() - selected },
                onExit = { exitSelection() },
                onTest = {
                    vm.testNodes(selected) { message ->
                        onToast(message)
                        exitSelection()
                    }
                },
                onGroup = {
                    vm.addToGroup(selected) { message ->
                        onToast(message)
                        exitSelection()
                    }
                },
                onDisable = {
                    val anyEnabled = nodes.any { it.id in selected && !it.disabled }
                    vm.setDisabledBatch(selected, anyEnabled) { message ->
                        onToast(message)
                        exitSelection()
                    }
                },
                onExport = {
                    val export = nodes.filter { it.id in selected }
                    if (export.isNotEmpty()) {
                        pendingExport = export
                        exportLauncher.launch("tts-nodes.json")
                    }
                },
                onDelete = { confirmBatchDelete = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 72.dp),
            )
        }
    }

    if (showAdd) {
        AddNodeSheet(
            adding = adding,
            groups = groups,
            onDismiss = { if (!adding) showAdd = false },
            onSave = { url, label, apiKey, group ->
                vm.addNode(url, label, apiKey, group) { message ->
                    showAdd = false
                    onToast(message)
                }
            },
        )
    }

    if (showBatchAdd) {
        BatchAddSheet(
            adding = importing,
            groups = groups,
            onDismiss = { if (!importing) showBatchAdd = false },
            onImport = { raw, group, autoTest ->
                vm.importNodes(raw, group, autoTest) { message ->
                    showBatchAdd = false
                    onToast(message)
                }
            },
        )
    }

    if (showGroupSelect) {
        GroupSelectSheet(
            nodes = nodes,
            initialSelected = config.groupIds.toSet(),
            onDismiss = { showGroupSelect = false },
            onSave = { members ->
                vm.setGroupMembers(members)
                showGroupSelect = false
                onToast("轮询池已保存：${members.size} 个节点")
            },
        )
    }

    if (confirmBatchDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除 $count 个节点？") },
            text = { Text("将删除 $count 个节点，并从轮询池中移除。此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBatchDelete = false
                        vm.deleteNodes(selected) { message ->
                            onToast(message)
                            exitSelection()
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") }
            },
        )
    }

    val detailNode = nodes.firstOrNull { it.id == detailId }
    if (detailNode != null) {
        val isDetailPrimary = detailNode.id == primaryId && !detailNode.disabled
        NodeDetailSheet(
            node = detailNode,
            mode = config.mode,
            isPrimary = isDetailPrimary,
            inGroup = detailNode.id in config.groupIds,
            isManual = detailNode.id == config.manualId,
            latencyMs = latencyByUrl[detailNode.baseUrl],
            now = now,
            testing = detailNode.id in busyNodeIds,
            onDismiss = { detailId = null },
            onTest = { vm.testNode(detailNode.id, onToast) },
            onToggleDisabled = {
                vm.setDisabled(detailNode.id, !detailNode.disabled, onToast)
            },
            onToggleGroup = { vm.toggleGroupMember(detailNode.id) },
            onSetManual = {
                vm.setManual(detailNode.id)
                onToast("已设为当前节点")
            },
            onEdit = {
                detailId = null
                editNodeId = detailNode.id
            },
            onDelete = { deleteNodeId = detailNode.id },
        )
    }

    val editNode = nodes.firstOrNull { it.id == editNodeId }
    if (editNode != null) {
        EditNodeSheet(
            node = editNode,
            groups = groups,
            onDismiss = { editNodeId = null },
            onSave = { label, apiKey, group ->
                vm.updateNodeMeta(editNode.id, label, apiKey, group)
                editNodeId = null
                onToast("节点信息已保存")
            },
        )
    }

    val deleteNode = nodes.firstOrNull { it.id == deleteNodeId }
    if (deleteNode != null) {
        AlertDialog(
            onDismissRequest = { deleteNodeId = null },
            title = { Text("删除节点？") },
            text = { Text("将删除「${deleteNode.label.ifBlank { deleteNode.baseUrl }}」，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteNode(deleteNode.id) { message ->
                            deleteNodeId = null
                            if (detailId == deleteNode.id) detailId = null
                            onToast(message)
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteNodeId = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodesHeader(
    count: Int,
    selectMode: Boolean,
    onSelect: () -> Unit,
    onAdd: () -> Unit,
    onLongAdd: () -> Unit,
) {
    PulseMainHeader(
        title = "节点",
        subtitle = "$count 个已配置",
        leading = { PulseBrandMark(size = 38) },
        actions = {
            HeaderIcon(
                icon = Icons.Rounded.Checklist,
                description = "多选",
                active = selectMode,
                onClick = onSelect,
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .combinedClickable(onClick = onAdd, onLongClick = onLongAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "添加节点",
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@Composable
private fun HeaderIcon(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(21.dp),
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hero(mode: CallMode) {
    val eyebrow = when (mode) {
        CallMode.AUTO -> "自动调度"
        CallMode.GROUP -> "按组调度"
        CallMode.MANUAL -> "手选节点"
    }
    val description = when (mode) {
        CallMode.AUTO -> "按健康度与延迟自动排序，失败 3 次进入 10 分钟冷却。"
        CallMode.GROUP -> "勾选参与轮询的节点，按列表顺序轮流调用。"
        CallMode.MANUAL -> "只使用选定节点，失败时自动切换到其他可用节点。"
    }
    Column(Modifier.padding(top = 14.dp, bottom = 18.dp)) {
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "声音从哪里来。",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 23.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryRow(available: Int, cooling: Int, disabled: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryCell("$available", "可用", StatusColors.Ok, Modifier.weight(1f))
        SummaryCell("$cooling", "冷却中", StatusColors.Warn, Modifier.weight(1f))
        SummaryCell("$disabled", "已停用", StatusColors.Muted, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = color,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeSegmented(
    mode: CallMode,
    onMode: (CallMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        CallMode.AUTO to "自动",
        CallMode.GROUP to "按组",
        CallMode.MANUAL to "手选",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = mode == value
            Surface(
                onClick = { onMode(value) },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                shape = RoundedCornerShape(9.dp),
                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = if (selected) 1.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: TtsNode,
    isPrimary: Boolean,
    badge: String,
    latencyMs: Long?,
    now: Long,
    selectMode: Boolean,
    checked: Boolean,
    showGroupToggle: Boolean,
    inGroup: Boolean,
    onClick: () -> Unit,
    onToggleCheck: () -> Unit,
    onToggleGroup: () -> Unit,
) {
    val state = node.uiState(now)
    val meta = if (latencyMs != null && state == NodeState.AVAILABLE) {
        "$latencyMs ms · ${node.group}"
    } else {
        "${node.statusLabel(now)} · ${node.group}"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isPrimary) 1.5.dp else 1.dp,
            if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = if (isPrimary) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (selectMode) {
                NodeSelectBox(checked = checked, onClick = onToggleCheck)
            } else {
                HealthTile(state)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        node.label.ifBlank { node.baseUrl },
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(7.dp))
                    NodeBadge(badge)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    node.baseUrl,
                    style = MonoTextStyle.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showGroupToggle) {
                GroupTogglePill(inGroup = inGroup, onClick = onToggleGroup)
            } else {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp, top = 4.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupTogglePill(inGroup: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (inGroup) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (inGroup) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (inGroup) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            if (inGroup) "已加入" else "加入",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddNodeButton(onClick: () -> Unit, onLongClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                        ),
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "添加节点",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GroupPoolPanel(
    nodes: List<TtsNode>,
    groupIds: List<String>,
    onSelect: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val selected = nodes.count { it.id in groupIds }
    val grouped = nodes.filter { it.id in groupIds }.groupBy { it.group }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "轮询池 · $selected 个节点",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "按顺序轮询，单节点失败自动切换。",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoolAction("选择节点", primary = true, onClick = onSelect)
                PoolAction("全选可用", primary = false, onClick = onSelectAll)
            }
            if (grouped.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    grouped.forEach { (group, members) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                "$group ${members.size}",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolAction(label: String, primary: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (primary) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HintRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "节点只保存地址与密钥；文本不会写入日志。",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
