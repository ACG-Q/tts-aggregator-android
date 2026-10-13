package com.example.ttsaggregator.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.ApiVersion
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.ui.appViewModel
import com.example.ttsaggregator.ui.components.ApiBadge
import com.example.ttsaggregator.ui.components.AppCard
import com.example.ttsaggregator.ui.components.Badge
import com.example.ttsaggregator.ui.components.EmptyState
import com.example.ttsaggregator.ui.components.HealthBadge
import com.example.ttsaggregator.ui.components.KeyBadge
import com.example.ttsaggregator.ui.components.SelectChip
import com.example.ttsaggregator.ui.theme.StatusColors
import com.example.ttsaggregator.viewmodel.AdminViewModel

/**
 * 节点管理 v2：
 * - 统计概览（点击即筛选）+ 状态筛选 chips
 * - 节点行点开 → 操作宫格（测试/标签/Key/停用/复制/编辑/删除）+ 行内编辑
 * - FAB 添加（底部弹窗，保存即测开关）
 * - 多选 → 底部浮动批量栏（全选/测试/禁用/删除）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val vm: AdminViewModel = appViewModel { AdminViewModel(it.nodeRepository, it.settings) }
    val nodes by vm.nodes.collectAsState()
    val testingIds by vm.testingIds.collectAsState()
    val batchTesting by vm.batchTesting.collectAsState()
    val testProgress by vm.testProgress.collectAsState()
    val clipboard = LocalClipboardManager.current

    var filter by remember { mutableStateOf(NodeFilter.ALL) }
    var sortMode by remember { mutableStateOf(SortMode.DEFAULT) }
    var openId by remember { mutableStateOf<String?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var autoTest by remember { mutableStateOf(true) }

    val filtered = remember(nodes, filter, query, sortMode) {
        var list = nodes.filter { filter.matches(it) }
        if (query.isNotBlank()) {
            list = list.filter {
                it.baseUrl.contains(query, true) || it.label.contains(query, true)
            }
        }
        when (sortMode) {
            SortMode.DEFAULT -> list.sortedWith(compareBy({ it.health.ordinal }, { -it.lastChecked }))
            SortMode.NAME -> list.sortedBy { it.label.ifBlank { it.baseUrl } }
            SortMode.LAST -> list.sortedByDescending { it.lastChecked }
            SortMode.FAIL -> list.sortedByDescending { it.failCount }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点管理（${nodes.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.List, contentDescription = "批量导入")
                    }
                    IconButton(onClick = {
                        selectMode = !selectMode
                        selected = emptySet()
                    }) {
                        Icon(Icons.Default.Checklist, contentDescription = "多选")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        floatingActionButton = {
            if (selectMode) {
                BatchBar(
                    count = selected.size,
                    onSelectAll = {
                        selected = if (selected.size == filtered.size) emptySet()
                        else filtered.map { it.id }.toSet()
                    },
                    onBatchTest = {
                        if (selected.isEmpty()) { onToast("先勾选节点"); return@BatchBar }
                        vm.testNodes(selected) { onToast(it) }
                    },
                    onBatchDisable = {
                        if (selected.isEmpty()) { onToast("先勾选节点"); return@BatchBar }
                        vm.setDisabled(selected, disabled = true)
                        selected = emptySet()
                        onToast("已停用所选节点")
                    },
                    onBatchDelete = {
                        if (selected.isEmpty()) { onToast("先勾选节点"); return@BatchBar }
                        val cnt = selected.size
                        vm.delete(selected)
                        selected = emptySet()
                        onToast("已删除 $cnt 个节点")
                    },
                )
            } else {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加节点")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- 统计概览（点击即筛选） ----
            item {
                StatsRow(
                    total = nodes.size,
                    ok = nodes.count { it.health == TtsNode.Health.OK && !it.disabled },
                    bad = nodes.count { it.health == TtsNode.Health.FAIL },
                    needKey = nodes.count { !it.keyless },
                    coolDis = nodes.count { it.isCoolingDown() || it.disabled },
                    active = filter,
                    onFilter = { filter = it },
                )
            }

            // ---- 状态筛选 chips ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NodeFilter.entries.forEach { f ->
                        SelectChip(f.label, selected = filter == f, onClick = { filter = f })
                    }
                }
            }

            // ---- 搜索 + 排序 ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索 URL / 标签", style = MaterialTheme.typography.labelMedium) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = {
                            sortMode = when (sortMode) {
                                SortMode.DEFAULT -> SortMode.NAME
                                SortMode.NAME -> SortMode.LAST
                                SortMode.LAST -> SortMode.FAIL
                                SortMode.FAIL -> SortMode.DEFAULT
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(sortMode.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ---- 测试进度 ----
            if (batchTesting) {
                item {
                    val p = testProgress
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                p?.let { "批量测试中 ${it.first}/${it.second}" } ?: "批量测试中…",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (p != null && p.second > 0) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { p.first.toFloat() / p.second },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ---- 空态 ----
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        if (nodes.isEmpty()) "暂无节点，点右下角 ＋ 添加" else "没有匹配的节点",
                    )
                }
            }

            // ---- 节点列表（可展开） ----
            itemsIndexed(filtered, key = { _, it -> it.id }) { idx, node ->
                NodeExpandCard(
                    node = node,
                    index = idx + 1,
                    isTesting = node.id in testingIds,
                    isOpen = openId == node.id,
                    selectMode = selectMode,
                    checked = node.id in selected,
                    editing = editId == node.id,
                    onToggleOpen = {
                        openId = if (openId == node.id) null else node.id
                        editId = null
                    },
                    onToggleCheck = {
                        selected = if (node.id in selected) selected - node.id else selected + node.id
                    },
                    onStartEdit = { editId = node.id },
                    onCancelEdit = { editId = null },
                    onSaveEdit = { label, apiKey ->
                        vm.updateNodeMeta(node.id, label, apiKey) { onToast(it) }
                        editId = null
                    },
                    onTest = {
                        if (batchTesting) return@NodeExpandCard
                        vm.testNode(node.id) { onToast(it) }
                    },
                    onToggleDisable = {
                        vm.setDisabled(listOf(node.id), disabled = !node.disabled)
                        onToast(if (!node.disabled) "已停用" else "已启用")
                    },
                    onCopyUrl = {
                        clipboard.setText(AnnotatedString(node.baseUrl))
                        onToast("URL 已复制")
                    },
                    onCopyKey = {
                        if (node.apiKey.isNullOrBlank()) onToast("该节点没有 Key")
                        else {
                            clipboard.setText(AnnotatedString(node.apiKey))
                            onToast("Key 已复制")
                        }
                    },
                    onDelete = {
                        vm.delete(listOf(node.id))
                        onToast("已删除")
                    },
                )
            }
        }
    }

    // ---- 添加底部弹窗（保存即测） ----
    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            AddSheetContent(
                autoTest = autoTest,
                onAutoTestChange = { autoTest = it },
                onSave = { url, label, key ->
                    vm.addNode(url, label, key, autoTest) { msg ->
                        showAdd = false
                        onToast(msg)
                    }
                },
            )
        }
    }

    // ---- 批量导入 ----
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onConfirm = { raw ->
                showImport = false
                vm.importNodes(raw) { onToast(it) }
            },
        )
    }
}

// ================================================================ 统计概览

@Composable
private fun StatsRow(
    total: Int,
    ok: Int,
    bad: Int,
    needKey: Int,
    coolDis: Int,
    active: NodeFilter,
    onFilter: (NodeFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatCard("全部", total, null, active == NodeFilter.ALL, Modifier.weight(1f)) { onFilter(NodeFilter.ALL) }
        StatCard("健康", ok, StatusColors.Ok, active == NodeFilter.OK, Modifier.weight(1f)) { onFilter(NodeFilter.OK) }
        StatCard("异常", bad, StatusColors.Error, active == NodeFilter.BAD, Modifier.weight(1f)) { onFilter(NodeFilter.BAD) }
        StatCard("需Key", needKey, StatusColors.Warn, active == NodeFilter.NEEDKEY, Modifier.weight(1f)) { onFilter(NodeFilter.NEEDKEY) }
        StatCard("停用", coolDis, StatusColors.Muted, active == NodeFilter.COOL_DIS, Modifier.weight(1f)) { onFilter(NodeFilter.COOL_DIS) }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    color: Color?,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Text(
                "$count",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
                color = color ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ================================================================ 可展开节点卡

@Composable
private fun NodeExpandCard(
    node: TtsNode,
    index: Int,
    isTesting: Boolean,
    isOpen: Boolean,
    selectMode: Boolean,
    checked: Boolean,
    editing: Boolean,
    onToggleOpen: () -> Unit,
    onToggleCheck: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: (label: String, apiKey: String?) -> Unit,
    onTest: () -> Unit,
    onToggleDisable: () -> Unit,
    onCopyUrl: () -> Unit,
    onCopyKey: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimmed = node.disabled || node.isCoolingDown()
    var editLabel by remember(editing, node.label) { mutableStateOf(node.label) }
    var editKey by remember(editing, node.apiKey) { mutableStateOf(node.apiKey ?: "") }

    AppCard(
        onClick = if (selectMode) onToggleCheck else null,
        containerColor = if (dimmed) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surface,
    ) {
        // 主行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleOpen),
        ) {
            if (selectMode) {
                Checkbox(checked = checked, onCheckedChange = { onToggleCheck() })
            } else {
                Text(
                    "$index",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (node.health == TtsNode.Health.OK && !node.disabled)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.label.ifBlank { node.baseUrl },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    node.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (isTesting) {
                // 测试中：折叠状态也可见的 loading
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    if (isOpen) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 徽章行
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (isTesting) {
                Badge("⏳ 测试中…", StatusColors.Info)
            }
            ApiBadge(node.apiVersion)
            HealthBadge(node.health)
            KeyBadge(node.keyless)
            if (node.voiceCount > 0) {
                Text(
                    "${node.voiceCount} 音色",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (node.isCoolingDown()) Badge("🧊 冷却中", StatusColors.Warn)
            if (node.disabled) Badge("已停用", StatusColors.Muted)
            if (node.failCount > 0) {
                Text(
                    "连败 ${node.failCount}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // ===== 展开操作区 =====
        if (isOpen) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 操作宫格（两行）
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionCell("▶", "测试", Modifier.weight(1f), isTesting, onTest)
                    ActionCell("🏷", "标签", Modifier.weight(1f), editing, onStartEdit)
                    ActionCell("🔑", if (node.keyless) "补Key" else "改Key", Modifier.weight(1f), editing, onStartEdit)
                    ActionCell(
                        if (node.disabled) "▶" else "⏸",
                        if (node.disabled) "启用" else "停用",
                        Modifier.weight(1f), false, onToggleDisable,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionCell("⧉", "复制URL", Modifier.weight(1f), false, onCopyUrl)
                    ActionCell("⧉", "复制Key", Modifier.weight(1f), false, onCopyKey)
                    ActionCell("✏️", "编辑", Modifier.weight(1f), editing, onStartEdit)
                    ActionCell("🗑", "删除", Modifier.weight(1f), false, onDelete, danger = true)
                }

                // 行内编辑区
                if (editing) {
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        label = { Text("标签", style = MaterialTheme.typography.labelMedium) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editKey,
                        onValueChange = { editKey = it },
                        label = { Text("API Key（留空 = 免 Key）", style = MaterialTheme.typography.labelMedium) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "保存后建议点「测试」刷新免Key/需Key 状态",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancelEdit) {
                            Text("取消", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(onClick = { onSaveEdit(editLabel, editKey.ifBlank { null }) }) {
                            Text("保存", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCell(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val contentColor = when {
        danger -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = contentColor,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            if (active && label == "测试") {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Text(icon, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ================================================================ 批量浮动栏

@Composable
private fun BatchBar(
    count: Int,
    onSelectAll: () -> Unit,
    onBatchTest: () -> Unit,
    onBatchDisable: () -> Unit,
    onBatchDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "已选 $count",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 2.dp),
            )
            BatchBtn("全选", onClick = onSelectAll)
            BatchBtn("测试", color = StatusColors.Ok, onClick = onBatchTest)
            BatchBtn("停用", onClick = onBatchDisable)
            BatchBtn("删除", color = StatusColors.Error, onClick = onBatchDelete)
        }
    }
}

@Composable
private fun BatchBtn(text: String, color: Color? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = color ?: MaterialTheme.colorScheme.primaryContainer,
        contentColor = color?.let { Color.White } ?: MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

// ================================================================ 添加底部弹窗

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheetContent(
    autoTest: Boolean,
    onAutoTestChange: (Boolean) -> Unit,
    onSave: (url: String, label: String, apiKey: String?) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("添加节点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL", style = MaterialTheme.typography.labelMedium) },
            placeholder = { Text("https://tts.example.com", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("标签（可选）", style = MaterialTheme.typography.labelMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Key（可选）", style = MaterialTheme.typography.labelMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(checked = autoTest, onCheckedChange = onAutoTestChange)
            Text(
                "保存后自动测试（推荐）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = {
                if (url.isNotBlank()) onSave(url, label, apiKey)
            },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存", style = MaterialTheme.typography.labelLarge) }
        Text(
            "批量粘贴：每行一个 URL，如 https://a.com|标签",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ================================================================ 批量导入弹窗

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (raw: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量导入 URL", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "每行一个 URL，可加标签（url|标签）。保存为单事务，万级也只写一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("URL 列表", style = MaterialTheme.typography.labelMedium) },
                    placeholder = {
                        Text("https://a.example.com\nhttps://b.example.com|我的节点", style = MaterialTheme.typography.bodySmall)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("保存", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.labelLarge) }
        },
    )
}

// ================================================================ 辅助

private enum class NodeFilter(val label: String) {
    ALL("全部"), OK("健康"), BAD("异常"), NEEDKEY("需Key"),
    V1("新版V1"), LEGACY("老版"), COOL_DIS("冷却/停用");

    fun matches(n: TtsNode): Boolean = when (this) {
        ALL -> true
        OK -> n.health == TtsNode.Health.OK && !n.disabled
        BAD -> n.health == TtsNode.Health.FAIL
        NEEDKEY -> !n.keyless
        V1 -> n.apiVersion == ApiVersion.V1
        LEGACY -> n.apiVersion == ApiVersion.LEGACY
        COOL_DIS -> n.isCoolingDown() || n.disabled
    }
}

private enum class SortMode(val label: String) {
    DEFAULT("默认序"), NAME("名称"), LAST("最近测试"), FAIL("失败次数"),
}
