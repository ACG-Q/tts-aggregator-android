package com.pulse.tts.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.tts.data.NodeRepository
import com.pulse.tts.data.SettingsStore
import com.pulse.tts.data.TtsNode
import com.pulse.tts.data.parseNodeImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 节点管理页：增删改查 + 单个/批量测试。
 * 测试进度（testingIds / batchTesting / testProgress）也由本 VM 统一管理。
 */
class AdminViewModel(
    private val repo: NodeRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val nodes: StateFlow<List<TtsNode>> = repo.nodes
    val poolConfig = settings.poolConfig

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private val _testingIds = MutableStateFlow<Set<String>>(emptySet())
    val testingIds: StateFlow<Set<String>> = _testingIds

    private val _batchTesting = MutableStateFlow(false)
    val batchTesting: StateFlow<Boolean> = _batchTesting

    private val _testProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val testProgress: StateFlow<Pair<Int, Int>?> = _testProgress

    // ---------- 增删改 ----------

    fun addNode(
        url: String,
        label: String,
        apiKey: String?,
        group: String,
        autoTest: Boolean = true,
        onDone: (String) -> Unit,
    ) {
        if (_adding.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _adding.value = true
            try {
                val trimmed = url.trim().removeSuffix("/")
                if (!Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(trimmed)) {
                    onDone("URL 无效（需以 http(s) 开头）")
                    return@launch
                }
                val node = repo.saveRaw(
                    baseUrl = trimmed,
                    label = label.trim(),
                    apiKey = apiKey?.trim()?.ifEmpty { null },
                    group = group,
                )
                if (autoTest) {
                    repo.probeAndUpdate(node.id)
                    onDone(repo.get(node.id)?.let { summary(it) } ?: "已保存：${node.baseUrl}")
                } else {
                    onDone("已保存：${node.baseUrl}（未测试）")
                }
            } catch (e: Exception) {
                Log.w(TAG, "addNode failed", e)
                onDone("保存失败: ${e.message}")
            } finally {
                _adding.value = false
            }
        }
    }

    fun importNodes(
        raw: String,
        group: String,
        autoTest: Boolean,
        onDone: (String) -> Unit,
    ) {
        if (_importing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _importing.value = true
            try {
                val drafts = parseNodeImport(raw).map { it.copy(group = group) }
                if (drafts.isEmpty()) {
                    onDone("没有有效行（需以 http 开头）")
                    return@launch
                }
                val existing = repo.nodes.value.map { it.baseUrl }.toSet()
                val unique = drafts.distinctBy { it.url }.filterNot { it.url in existing }
                val skipped = drafts.size - unique.size
                if (unique.isEmpty()) {
                    onDone("没有新节点，已跳过重复 $skipped 个")
                    return@launch
                }
                repo.saveRawBatch(unique)
                if (autoTest) {
                    val addedUrls = unique.map { it.url }.toSet()
                    val addedIds = repo.nodes.value
                        .filter { it.baseUrl in addedUrls }
                        .map { it.id }
                    repo.probeAll(addedIds)
                }
                val skippedMsg = if (skipped > 0) "，跳过重复 $skipped 个" else ""
                onDone("已导入 ${unique.size} 个节点$skippedMsg")
            } catch (e: Exception) {
                Log.w(TAG, "importNodes failed", e)
                onDone("导入失败: ${e.message}")
            } finally {
                _importing.value = false
            }
        }
    }

    fun updateNodeMeta(
        id: String,
        label: String,
        apiKey: String?,
        group: String,
        onDone: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.updateNodeMeta(id, label.trim(), apiKey?.trim()?.ifEmpty { null }, group)
                onDone("已更新节点信息")
            } catch (e: Exception) {
                Log.w(TAG, "updateNodeMeta failed", e)
                onDone("更新失败: ${e.message}")
            }
        }
    }

    fun addToGroup(ids: Collection<String>, onDone: (String) -> Unit) {
        if (ids.isEmpty()) {
            onDone("先勾选节点")
            return
        }
        settings.updatePoolConfig {
            it.copy(groupIds = (it.groupIds + ids).distinct(), groupCursor = 0)
        }
        onDone("已加入轮询池：${ids.size} 个节点")
    }

    fun setManual(id: String) {
        settings.updatePoolConfig { it.copy(manualId = id, manualFailCount = 0) }
    }

    fun toggleGroupMember(id: String) {
        settings.updatePoolConfig { config ->
            val ids = if (id in config.groupIds) config.groupIds - id else config.groupIds + id
            config.copy(groupIds = ids, groupCursor = 0)
        }
    }

    /** 批量启用/停用（IO 线程写库，避免主线程磁盘 IO） */
    fun setDisabled(ids: Collection<String>, disabled: Boolean, onDone: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.setDisabled(ids, disabled)
                onDone(if (disabled) "已停用 ${ids.size} 个节点" else "已启用 ${ids.size} 个节点")
            } catch (e: Exception) {
                Log.w(TAG, "setDisabled failed", e)
                onDone("操作失败: ${e.message}")
            }
        }
    }

    /** 批量删除；同时清理手选/组成员中引用到的已删节点 */
    fun delete(ids: Collection<String>, onDone: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                settings.updatePoolConfig { cur ->
                    var next = cur
                    if (cur.manualId != null && cur.manualId in ids) {
                        next = next.copy(manualId = null, manualFailCount = 0)
                    }
                    val remaining = cur.groupIds.filterNot { it in ids }
                    if (remaining.size != cur.groupIds.size) {
                        next = next.copy(groupIds = remaining, groupCursor = 0)
                    }
                    next
                }
                repo.delete(ids)
                onDone("已删除 ${ids.size} 个节点")
            } catch (e: Exception) {
                Log.w(TAG, "delete failed", e)
                onDone("删除失败: ${e.message}")
            }
        }
    }

    // ---------- 测试 ----------

    fun testNode(id: String, onDone: (String) -> Unit) {
        testNodes(listOf(id), onDone)
    }

    /** 批量测试指定节点（顺序执行，带进度状态） */
    fun testNodes(ids: Collection<String>, onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ids.isEmpty()) {
                onDone("列表为空")
                return@launch
            }
            _testingIds.value = ids.toSet()
            _testProgress.value = 0 to ids.size
            try {
                ids.forEachIndexed { i, id ->
                    repo.probeAndUpdate(id)
                    _testProgress.value = (i + 1) to ids.size
                }
                val ok = repo.nodes.value.count { it.id in ids && it.health == TtsNode.Health.OK }
                onDone("测试完成：${ids.size} 个节点，健康 $ok 个")
            } catch (e: Exception) {
                onDone("测试失败: ${e.message}")
            } finally {
                _testingIds.value = emptySet()
                _testProgress.value = null
            }
        }
    }

    /** 全量批量测试（并发分批，带进度回调） */
    fun testAll(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = repo.nodes.value.map { it.id }
            if (ids.isEmpty()) {
                onDone("列表为空")
                return@launch
            }
            _batchTesting.value = true
            _testProgress.value = 0 to ids.size
            try {
                repo.probeAll(ids) { done, total -> _testProgress.value = done to total }
                val ok = repo.nodes.value.count { it.health == TtsNode.Health.OK }
                onDone("批量测试完成：共 ${ids.size} 个，健康 $ok 个")
            } catch (e: Exception) {
                onDone("批量测试失败: ${e.message}")
            } finally {
                _batchTesting.value = false
                _testProgress.value = null
            }
        }
    }

    private fun summary(n: TtsNode): String {
        val health = when (n.health) {
            TtsNode.Health.OK -> "健康"
            TtsNode.Health.FAIL -> "异常"
            TtsNode.Health.UNKNOWN -> "未测试"
        }
        return "${n.baseUrl} — $health / ${if (n.keyless) "免Key" else "需Key"} / ${n.voiceCount} 音色"
    }

    companion object {
        private const val TAG = "AdminViewModel"
    }
}
