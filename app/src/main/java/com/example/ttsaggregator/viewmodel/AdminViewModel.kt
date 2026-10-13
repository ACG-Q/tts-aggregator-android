package com.example.ttsaggregator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ttsaggregator.data.NodeRepository
import com.example.ttsaggregator.data.SettingsStore
import com.example.ttsaggregator.data.TtsNode
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
        autoTest: Boolean = true,
        onDone: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmed = url.trim().removeSuffix("/")
                if (!trimmed.startsWith("http")) {
                    onDone("URL 无效（需以 http 开头）")
                    return@launch
                }
                val node = repo.saveRaw(trimmed, label.trim(), apiKey?.trim()?.ifEmpty { null })
                if (autoTest) {
                    // 保存即测：立刻探测并更新状态
                    repo.probeAndUpdate(node.id)
                    onDone(repo.get(node.id)?.let { summary(it) } ?: "已保存：${node.baseUrl}")
                } else {
                    onDone("已保存：${node.baseUrl}（未测试）")
                }
            } catch (e: Exception) {
                onDone("保存失败: ${e.message}")
            }
        }
    }

    fun importNodes(raw: String, onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = raw.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapNotNull { line ->
                        val parts = line.split("|")
                        val url = parts[0].trim().removeSuffix("/")
                        if (!url.startsWith("http")) null
                        else Triple(url, parts.getOrNull(1)?.trim().orEmpty(), null)
                    }
                if (entries.isEmpty()) {
                    onDone("没有有效行（需以 http 开头）")
                    return@launch
                }
                repo.saveRawBatch(entries)
                onDone("已保存 ${entries.size} 个节点（单事务写入）")
            } catch (e: Exception) {
                onDone("导入失败: ${e.message}")
            }
        }
    }

    fun updateNodeMeta(id: String, label: String, apiKey: String?, onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.updateNodeMeta(id, label.trim(), apiKey?.trim()?.ifEmpty { null })
                onDone("已更新节点信息")
            } catch (e: Exception) {
                onDone("更新失败: ${e.message}")
            }
        }
    }

    /** 批量启用/停用 */
    fun setDisabled(ids: Collection<String>, disabled: Boolean) {
        repo.setDisabled(ids, disabled)
    }

    fun delete(ids: Collection<String>) {
        // 删除手选节点时清理配置引用
        settings.updatePoolConfig { cur ->
            if (cur.manualId != null && cur.manualId in ids) cur.copy(manualId = null, manualFailCount = 0)
            else cur
        }
        repo.delete(ids)
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
}
