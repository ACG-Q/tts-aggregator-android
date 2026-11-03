package com.example.ttsaggregator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.NodeFilter
import com.example.ttsaggregator.data.NodeSort
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.data.exportNodesJson
import com.example.ttsaggregator.data.filterAndSortNodes
import com.example.ttsaggregator.data.knownGroups
import com.example.ttsaggregator.ui.LocalAppSettings
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.AddNodeSheet
import com.example.ttsaggregator.ui.components.AppTextField
import com.example.ttsaggregator.ui.components.BatchAddSheet
import com.example.ttsaggregator.ui.components.BatchBar
import com.example.ttsaggregator.ui.components.EditNodeSheet
import com.example.ttsaggregator.ui.components.EmptyState
import com.example.ttsaggregator.ui.components.HealthTile
import com.example.ttsaggregator.ui.components.NodeDetailSheet
import com.example.ttsaggregator.ui.components.NodeSelectBox
import com.example.ttsaggregator.ui.components.PulseSubHeader
import com.example.ttsaggregator.ui.components.SelectChip
import com.example.ttsaggregator.ui.theme.MonoTextStyle
import com.example.ttsaggregator.ui.theme.StatusColors
import com.example.ttsaggregator.ui.util.relativeTimeLabel
import com.example.ttsaggregator.ui.util.statusLabel
import com.example.ttsaggregator.ui.util.uiState
import com.example.ttsaggregator.viewmodel.AdminViewModel
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * 节点管理页：统计筛选、搜索排序、卡片详情、批量维护与导入导出。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val vm: AdminViewModel = appViewModel { AdminViewModel(it.nodeRepository, it.settings) }
    val nodes by vm.nodes.collectAsState()
    val testingIds by vm.testingIds.collectAsState()
    val testProgress by vm.testProgress.collectAsState()
    val adding by vm.adding.collectAsState()
    val importing by vm.importing.collectAsState()
    val config by vm.poolConfig.collectAsState()
    val context = LocalContext.current
    val appSettings = LocalAppSettings.current
    val haptics = LocalHapticFeedback.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NodeFilter.ALL) }
    var sort by remember { mutableStateOf(NodeSort.DEFAULT) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var editNodeId by remember { mutableStateOf<String?>(null) }
    var deleteNodeId by remember { mutableStateOf<String?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showBatchAdd by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<List<TtsNode>>(emptyList()) }

    fun exitSelection() {
        selectMode = false
        selected = emptySet()
    }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val groups = remember(nodes) { knownGroups(nodes) }
    val filtered = remember(nodes, query, filter, sort, now) {
        filterAndSortNodes(nodes, query, filter, sort, now)
    }
    val filteredIds = remember(filtered) { filtered.map { it.id }.toSet() }
    val allSelected = filteredIds.isNotEmpty() && selected.containsAll(filteredIds)

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

    fun longPressHint() {
        if (appSettings.haptics) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(
                title = "节点管理",
                subtitle = "测试、分组与批量维护",
                onBack = onBack,
            ) {
                HeaderAction(
                    icon = Icons.Rounded.Checklist,
                    description = "多选",
                    active = selectMode,
                    onClick = { if (selectMode) exitSelection() else selectMode = true },
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .combinedClickable(
                            onClick = { showAdd = true },
                            onLongClick = {
                                longPressHint()
                                showBatchAdd = true
                            },
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "添加节点",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 6.dp,
                    bottom = if (selectMode) 280.dp else 108.dp,
                ),
            ) {
                item {
                    StatsGrid(
                        nodes = nodes,
                        filter = filter,
                        now = now,
                        onFilter = { filter = it },
                    )
                }
                item {
                    ToolRow(
                        query = query,
                        onQuery = { query = it },
                        sort = sort,
                        onSort = { sort = sort.next() },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NodeFilter.entries.forEach { option ->
                            SelectChip(
                                label = option.label,
                                selected = filter == option,
                                onClick = { filter = option },
                            )
                        }
                    }
                }
                item {
                    HintBar(Modifier.padding(top = 12.dp, bottom = 12.dp))
                }

                val progress = testProgress
                if (progress != null) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "正在测试 ${progress.first}/${progress.second}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                if (progress.second > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress.first.toFloat() / progress.second },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            if (nodes.isEmpty()) "还没有节点。长按右上角 ＋ 批量导入，或点击 ＋ 添加。"
                            else "没有匹配的节点，试试调整搜索或筛选。",
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                items(filtered, key = { it.id }) { node ->
                    NodeAdminCard(
                        node = node,
                        now = now,
                        checked = node.id in selected,
                        testing = node.id in testingIds,
                        onToggleCheck = {
                            val next = if (node.id in selected) selected - node.id
                            else selected + node.id
                            selected = next
                            selectMode = next.isNotEmpty()
                        },
                        onOpen = { detailId = node.id },
                    )
                    Spacer(Modifier.height(10.dp))
                }

                item {
                    AddNodeButton(
                        onClick = { showAdd = true },
                        onLongClick = {
                            longPressHint()
                            showBatchAdd = true
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (selectMode) {
                BatchBar(
                    selectedCount = selected.size,
                    allSelected = allSelected,
                    showGroupAction = true,
                    showExportAction = true,
                    onSelectAll = {
                        selected = if (allSelected) emptySet() else filteredIds
                    },
                    onInvert = { selected = filteredIds - selected },
                    onExit = { exitSelection() },
                    onTest = {
                        if (selected.isEmpty()) {
                            onToast("先勾选节点")
                        } else {
                            vm.testNodes(selected) { message ->
                                onToast(message)
                                exitSelection()
                            }
                        }
                    },
                    onGroup = {
                        vm.addToGroup(selected) { message ->
                            onToast(message)
                            exitSelection()
                        }
                    },
                    onDisable = {
                        if (selected.isEmpty()) {
                            onToast("先勾选节点")
                        } else {
                            val anyEnabled = nodes.any { it.id in selected && !it.disabled }
                            vm.setDisabled(selected, anyEnabled) { message ->
                                onToast(message)
                                exitSelection()
                            }
                        }
                    },
                    onExport = {
                        val export = nodes.filter { it.id in selected }
                        if (export.isNotEmpty()) {
                            pendingExport = export
                            exportLauncher.launch("tts-nodes.json")
                        }
                    },
                    onDelete = {
                        if (selected.isEmpty()) onToast("先勾选节点") else confirmBatchDelete = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 18.dp),
                )
            }
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

    val detailNode = nodes.firstOrNull { it.id == detailId }
    if (detailNode != null) {
        val isDetailPrimary = detailNode.id == primaryId && !detailNode.disabled
        NodeDetailSheet(
            node = detailNode,
            mode = config.mode,
            isPrimary = isDetailPrimary,
            inGroup = detailNode.id in config.groupIds,
            isManual = detailNode.id == config.manualId,
            latencyMs = null,
            now = now,
            testing = detailNode.id in testingIds,
            onDismiss = { detailId = null },
            onTest = { vm.testNode(detailNode.id, onToast) },
            onToggleDisabled = {
                vm.setDisabled(listOf(detailNode.id), !detailNode.disabled, onDone = onToast)
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
            onDelete = {
                detailId = null
                deleteNodeId = detailNode.id
            },
        )
    }

    val editNode = nodes.firstOrNull { it.id == editNodeId }
    if (editNode != null) {
        EditNodeSheet(
            node = editNode,
            groups = groups,
            onDismiss = { editNodeId = null },
            onSave = { label, apiKey, group ->
                vm.updateNodeMeta(editNode.id, label, apiKey, group) { message ->
                    editNodeId = null
                    onToast(message)
                }
            },
        )
    }

    val deleteNode = nodes.firstOrNull { it.id == deleteNodeId }
    if (deleteNode != null) {
        AlertDialog(
            onDismissRequest = { deleteNodeId = null },
            title = { Text("删除节点？") },
            text = { Text("将删除「${deleteNode.label.ifBlank { deleteNode.baseUrl }}」，并从轮询池中移除。此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(listOf(deleteNode.id)) { message ->
                            deleteNodeId = null
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
                        vm.delete(selected) { message ->
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
}

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatsGrid(
    nodes: List<TtsNode>,
    filter: NodeFilter,
    now: Long,
    onFilter: (NodeFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCell(
            label = "全部",
            count = nodes.size,
            accent = MaterialTheme.colorScheme.primary,
            active = filter == NodeFilter.ALL,
            onClick = { onFilter(NodeFilter.ALL) },
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = "健康",
            count = nodes.count { NodeFilter.OK.matches(it, now) },
            accent = StatusColors.Ok,
            active = filter == NodeFilter.OK,
            onClick = { onFilter(NodeFilter.OK) },
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = "异常",
            count = nodes.count { NodeFilter.BAD.matches(it, now) },
            accent = StatusColors.Error,
            active = filter == NodeFilter.BAD,
            onClick = { onFilter(NodeFilter.BAD) },
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = "停用",
            count = nodes.count { NodeFilter.DISABLED.matches(it, now) },
            accent = StatusColors.Muted,
            active = filter == NodeFilter.DISABLED,
            onClick = { onFilter(NodeFilter.DISABLED) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    count: Int,
    accent: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (active) 1.5.dp else 1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = accent,
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
private fun ToolRow(
    query: String,
    onQuery: (String) -> Unit,
    sort: NodeSort,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = "搜索名称、URL 或分组",
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Surface(
            onClick = onSort,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    sort.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HintBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "批量操作可直接勾选，或点右上角清单图标进入多选模式。长按 ＋ 批量导入 URL。",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NodeAdminCard(
    node: TtsNode,
    now: Long,
    checked: Boolean,
    testing: Boolean,
    onToggleCheck: () -> Unit,
    onOpen: () -> Unit,
) {
    val state = node.uiState(now)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (checked) 1.5.dp else 1.dp,
            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NodeSelectBox(checked = checked, onClick = onToggleCheck)
            Spacer(Modifier.width(10.dp))
            HealthTile(state)
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpen)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                node.group,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (node.lastChecked <= 0L) {
                            node.statusLabel(now)
                        } else {
                            "${node.statusLabel(now)} · ${relativeTimeLabel(node.lastChecked, now)}"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        node.baseUrl,
                        style = MonoTextStyle.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(15.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(18.dp),
                    )
                }
            }
        }
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
