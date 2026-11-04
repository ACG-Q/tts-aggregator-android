package com.pulse.tts.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 节点仓库：节点 CRUD + 探测编排。
 *
 * - 存储 → [NodeStore]（SQLite，增量写）
 * - 网络 → [TtsApiClient]（探测/元数据，纯 HTTP）
 * - 以 [StateFlow] 暴露节点列表，任何写操作后自动刷新，UI 直接 collect。
 */
class NodeRepository(
    private val context: Context,
    private val store: NodeStore,
    private val api: TtsApiClient,
) {

    private val _nodes = MutableStateFlow<List<TtsNode>>(emptyList())
    val nodes: StateFlow<List<TtsNode>> = _nodes

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()

    init {
        // 建库/迁移/首次全表读取都放到 IO，避免冷启动阻塞主线程
        scope.launch {
            try {
                migrateLegacyIfNeeded()
                val loaded = store.getAll()
                _nodes.value = _nodes.value.fold(loaded) { acc, n -> acc.withUpserted(n) }
            } catch (e: Exception) {
                Log.e(TAG, "load nodes failed", e)
            } finally {
                ready.complete(Unit)
            }
        }
    }

    /** 等待首次加载完成（调度入口调用，避免启动瞬间误判“没有节点”） */
    suspend fun awaitReady() {
        ready.await()
    }

    fun get(id: String): TtsNode? = _nodes.value.find { it.id == id }

    fun searchNodes(query: String, limit: Int, offset: Int): List<TtsNode> =
        store.search(query, limit, offset)

    fun countNodes(query: String = ""): Int =
        if (query.isEmpty()) store.count() else store.searchCount(query)

    // ---------- 写操作 ----------

    /** 按 baseUrl upsert 整个节点（供调度状态更新：失败计数/冷却/禁用等） */
    fun upsert(node: TtsNode) {
        store.insertOrUpdate(node)
        _nodes.update { it.withUpserted(node) }
    }

    fun delete(ids: Collection<String>) {
        store.delete(ids)
        _nodes.update { list -> list.filterNot { it.id in ids } }
    }

    /** 批量启用/停用 */
    fun setDisabled(ids: Collection<String>, disabled: Boolean) {
        store.setDisabled(ids, disabled)
        _nodes.update { list ->
            list.map { if (it.id in ids) it.copy(disabled = disabled) else it }
        }
    }

    /** 编辑节点元信息（标签 / API Key / 分组）；改 Key 后保持原 keyless 判定，重新测试可刷新 */
    fun updateNodeMeta(
        id: String,
        label: String,
        apiKey: String?,
        group: String? = null,
    ) {
        val node = get(id) ?: return
        val updated = node.copy(label = label, apiKey = apiKey, group = group ?: node.group)
        store.insertOrUpdate(updated)
        _nodes.update { it.withUpserted(updated) }
    }

    /** 纯保存：任意链接直接入库，不做网络请求。 */
    fun saveRaw(
        baseUrl: String,
        label: String = "",
        apiKey: String? = null,
        group: String = "默认",
    ): TtsNode {
        val node = TtsNode(
            baseUrl = baseUrl.removeTrailingSlash(),
            label = label,
            group = group,
            apiKey = apiKey,
            health = TtsNode.Health.UNKNOWN,
        )
        store.insertOrUpdate(node)
        _nodes.update { it.withUpserted(node) }
        return node
    }

    /** 批量纯保存（单事务，万级节点一次写完） */
    fun saveRawBatch(entries: List<NodeDraft>) {
        val nodes = entries.map { draft ->
            TtsNode(
                baseUrl = draft.url.removeTrailingSlash(),
                label = draft.label,
                apiKey = draft.apiKey,
                group = draft.group,
                health = TtsNode.Health.UNKNOWN,
            )
        }
        store.insertOrUpdateAll(nodes)
        _nodes.update { current -> nodes.fold(current) { acc, n -> acc.withUpserted(n) } }
    }

    // ---------- 探测 ----------

    /**
     * 探测已有节点并更新结果。
     * @param refresh 是否立刻刷新 StateFlow（批量探测时关闭，整批结束后统一刷，避免万级节点逐行重读全表）
     */
    suspend fun probeAndUpdate(id: String): TtsNode? {
        val node = get(id) ?: return null
        val probed = api.probe(node.baseUrl, node.label, node.apiKey).mergeProbe(node)
        store.insertOrUpdate(probed)
        _nodes.update { it.withUpserted(probed) }
        return probed
    }

    /** 批量探测（每批 [BATCH_SIZE] 个并发，带进度回调；每批结束统一刷新一次列表） */
    suspend fun probeAll(
        ids: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<TtsNode> {
        val result = mutableListOf<TtsNode>()
        var done = 0
        for (batch in ids.chunked(BATCH_SIZE)) {
            coroutineScope {
                batch.map { id -> async { probeAndUpdate(id) } }
                    .awaitAll()
                    .filterNotNull()
                    .forEach { result.add(it) }
            }
            done += batch.size
            onProgress(minOf(done, ids.size), ids.size)
        }
        return result
    }

    // ---------- 一次性迁移（旧 nodes.json / 更早的 SharedPrefs） ----------

    private fun migrateLegacyIfNeeded() {
        if (store.count() > 0) return
        // 1) 上一版的 nodes.json
        File(context.filesDir, "nodes.json").takeIf { it.exists() }?.let { f ->
            runCatching {
                AppJson.decodeFromString(ListSerializer(TtsNode.serializer()), f.readText())
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                store.insertOrUpdateAll(list)
                Log.i(TAG, "migrated ${list.size} nodes from nodes.json")
                return
            }
        }
        // 2) 最早版本的 SharedPrefs
        context.getSharedPreferences("tts_nodes", Context.MODE_PRIVATE)
            .getString("nodes", null)?.let { raw ->
                runCatching {
                    AppJson.decodeFromString(ListSerializer(TtsNode.serializer()), raw)
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                    store.insertOrUpdateAll(list)
                    Log.i(TAG, "migrated ${list.size} nodes from legacy prefs")
                }
            }
    }

    companion object {
        private const val TAG = "TTS-Agg"
        private const val BATCH_SIZE = 5
    }
}

/** 与存储层 ORDER BY 一致：健康优先，其次最近测试时间倒序。 */
internal val NodeOrder: Comparator<TtsNode> =
    compareBy<TtsNode> { it.health.ordinal }.thenByDescending { it.lastChecked }

/** 本地增量更新列表，避免每次写入都重读全表。 */
internal fun List<TtsNode>.withUpserted(node: TtsNode): List<TtsNode> =
    (filterNot { it.id == node.id } + node).sortedWith(NodeOrder)
