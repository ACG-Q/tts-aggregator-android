package com.example.ttsaggregator.viewmodel

import androidx.lifecycle.ViewModel
import com.example.ttsaggregator.data.NodeRepository
import com.example.ttsaggregator.data.SettingsStore
import com.example.ttsaggregator.data.TtsNode
import kotlinx.coroutines.flow.StateFlow

/**
 * 节点列表页（调用逻辑子页）：按当前模式选择/配置调度成员。
 */
class NodesViewModel(
    private val repo: NodeRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val nodes: StateFlow<List<TtsNode>> = repo.nodes
    val poolConfig = settings.poolConfig

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

    /** 自动模式：切换节点是否参与调度 */
    fun toggleDisabled(id: String) {
        val node = nodes.value.find { it.id == id } ?: return
        repo.setDisabled(listOf(id), disabled = !node.disabled)
    }
}
