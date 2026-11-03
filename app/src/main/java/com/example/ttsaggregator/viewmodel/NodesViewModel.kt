package com.example.ttsaggregator.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ttsaggregator.data.CallMode
import com.example.ttsaggregator.data.LogStore
import com.example.ttsaggregator.data.NodeRepository
import com.example.ttsaggregator.data.SettingsStore
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.data.parseNodeImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 节点列表页（调用逻辑子页）：按当前模式选择/配置调度成员。
 */
class NodesViewModel(
    private val repo: NodeRepository,
    private val settings: SettingsStore,
    logs: LogStore,
) : ViewModel() {

    val nodes: StateFlow<List<TtsNode>> = repo.nodes
    val callLogs = logs.logs
    val poolConfig = settings.poolConfig

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding

    private val _busyNodeIds = MutableStateFlow<Set<String>>(emptySet())
    val busyNodeIds: StateFlow<Set<String>> = _busyNodeIds

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    fun testNode(id: String, onDone: (String) -> Unit) {
        if (id in _busyNodeIds.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _busyNodeIds.value = _busyNodeIds.value + id
            val message = try {
                val node = repo.probeAndUpdate(id)
                when (node?.health) {
                    TtsNode.Health.OK -> "连接正常"
                    TtsNode.Health.FAIL -> "连接测试失败"
                    else -> "测试完成"
                }
            } catch (e: Exception) {
                Log.w(TAG, "testNode failed", e)
                "测试失败：${e.message ?: "未知错误"}"
            } finally {
                _busyNodeIds.value = _busyNodeIds.value - id
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun deleteNode(id: String, onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                repo.delete(listOf(id))
                "节点已删除"
            } catch (e: Exception) {
                Log.w(TAG, "deleteNode failed", e)
                "删除失败：${e.message ?: "未知错误"}"
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun updateNodeMeta(id: String, label: String, apiKey: String?, group: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateNodeMeta(id, label, apiKey, group)
        }
    }

    fun setDisabled(id: String, disabled: Boolean, onDone: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                repo.setDisabled(listOf(id), disabled)
                if (disabled) "节点已停用" else "节点已启用"
            } catch (e: Exception) {
                Log.w(TAG, "setDisabled failed", e)
                "操作失败：${e.message ?: "未知错误"}"
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    /**
     * 先保存节点，保存成功立即回调，弹层随即关闭；随后在后台探测连接，
     * 探测结果再通过 [onDone] 回调一次（失败也保留节点，便于后续重试）。
     */
    fun addNode(
        url: String,
        label: String,
        apiKey: String?,
        group: String = "默认",
        onDone: (String) -> Unit,
    ) {
        if (_adding.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _adding.value = true
            var savedId: String? = null
            var saveMessage = "保存失败"
            try {
                val trimmed = url.trim().removeSuffix("/")
                if (!Regex("^https?://.+", RegexOption.IGNORE_CASE).matches(trimmed)) {
                    saveMessage = "URL 无效（需以 http(s) 开头）"
                } else {
                    val node = repo.saveRaw(
                        baseUrl = trimmed,
                        label = label.trim().ifBlank { trimmed },
                        apiKey = apiKey?.trim()?.ifEmpty { null },
                        group = group,
                    )
                    savedId = node.id
                    saveMessage = "节点已保存，正在测试连接…"
                }
            } catch (e: Exception) {
                Log.w(TAG, "addNode failed", e)
                saveMessage = "保存失败：${e.message ?: "未知错误"}"
            } finally {
                _adding.value = false
            }
            withContext(Dispatchers.Main) { onDone(saveMessage) }

            val id = savedId ?: return@launch
            val testMessage = try {
                repo.probeAndUpdate(id)
                when (repo.get(id)?.health) {
                    TtsNode.Health.OK -> "连接正常"
                    TtsNode.Health.FAIL -> "连接测试失败，节点已保留"
                    else -> "节点已保存"
                }
            } catch (e: Exception) {
                Log.w(TAG, "addNode probe failed", e)
                "连接测试失败，节点已保留"
            }
            withContext(Dispatchers.Main) { onDone(testMessage) }
        }
    }

    fun testNodes(ids: Collection<String>, onDone: (String) -> Unit) {
        if (ids.isEmpty()) return onDone("先勾选节点")
        viewModelScope.launch(Dispatchers.IO) {
            _busyNodeIds.value = ids.toSet()
            val message = try {
                repo.probeAll(ids.toList())
                val ok = repo.nodes.value.count { it.id in ids && it.health == TtsNode.Health.OK }
                "测试完成：${ids.size} 个节点，健康 $ok 个"
            } catch (e: Exception) {
                Log.w(TAG, "testNodes failed", e)
                "测试失败：${e.message ?: "未知错误"}"
            } finally {
                _busyNodeIds.value = emptySet()
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun deleteNodes(ids: Collection<String>, onDone: (String) -> Unit) {
        if (ids.isEmpty()) return onDone("先勾选节点")
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                settings.updatePoolConfig { config ->
                    config.copy(
                        groupIds = config.groupIds.filterNot { it in ids },
                        manualId = config.manualId?.takeIf { it !in ids },
                        manualFailCount = if (config.manualId in ids) 0 else config.manualFailCount,
                    )
                }
                repo.delete(ids)
                "已删除 ${ids.size} 个节点"
            } catch (e: Exception) {
                Log.w(TAG, "deleteNodes failed", e)
                "删除失败：${e.message ?: "未知错误"}"
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun setDisabledBatch(ids: Collection<String>, disabled: Boolean, onDone: (String) -> Unit) {
        if (ids.isEmpty()) return onDone("先勾选节点")
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                repo.setDisabled(ids, disabled)
                if (disabled) "已停用 ${ids.size} 个节点" else "已启用 ${ids.size} 个节点"
            } catch (e: Exception) {
                Log.w(TAG, "setDisabledBatch failed", e)
                "操作失败：${e.message ?: "未知错误"}"
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun addToGroup(ids: Collection<String>, onDone: (String) -> Unit) {
        if (ids.isEmpty()) return onDone("先勾选节点")
        settings.updatePoolConfig {
            it.copy(groupIds = (it.groupIds + ids).distinct(), groupCursor = 0)
        }
        onDone("已加入轮询池：${ids.size} 个节点")
    }

    fun setGroupMembers(ids: Set<String>) {
        settings.updatePoolConfig { it.copy(groupIds = ids.toList(), groupCursor = 0) }
    }

    fun importNodes(raw: String, group: String, autoTest: Boolean, onDone: (String) -> Unit) {
        if (_importing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _importing.value = true
            val message = try {
                val drafts = parseNodeImport(raw).map { it.copy(group = group) }
                if (drafts.isEmpty()) {
                    "没有可导入的节点"
                } else {
                    val existing = repo.nodes.value.map { it.baseUrl }.toSet()
                    val unique = drafts.distinctBy { it.url }.filterNot { it.url in existing }
                    val skipped = drafts.size - unique.size
                    if (unique.isEmpty()) {
                        "没有新节点，跳过重复 $skipped 个"
                    } else {
                        repo.saveRawBatch(unique)
                        if (autoTest) {
                            val addedUrls = unique.map { it.url }.toSet()
                            val addedIds = repo.nodes.value
                                .filter { it.baseUrl in addedUrls }
                                .map { it.id }
                            repo.probeAll(addedIds)
                        }
                        "已导入 ${unique.size} 个节点，跳过重复 $skipped 个"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "importNodes failed", e)
                "导入失败：${e.message ?: "未知错误"}"
            } finally {
                _importing.value = false
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun setMode(mode: CallMode) {
        settings.updatePoolConfig { it.copy(mode = mode) }
    }

    /** 组模式：勾选/取消组成员 */
    fun toggleGroupMember(id: String) {
        settings.updatePoolConfig { cur ->
            val group = if (id in cur.groupIds) cur.groupIds - id else cur.groupIds + id
            cur.copy(groupIds = group, groupCursor = 0)
        }
    }

    /** 手选模式：指定/取消手选节点 */
    fun setManual(id: String) {
        settings.updatePoolConfig { cur ->
            if (cur.manualId == id) cur.copy(manualId = null, manualFailCount = 0)
            else cur.copy(manualId = id, manualFailCount = 0)
        }
    }

    /** 自动模式：切换节点是否参与调度（IO 线程写库） */
    fun toggleDisabled(id: String) {
        val node = nodes.value.find { it.id == id } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.setDisabled(listOf(id), disabled = !node.disabled)
            } catch (e: Exception) {
                Log.w(TAG, "toggleDisabled failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "NodesViewModel"
    }
}
