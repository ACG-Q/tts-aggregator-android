package com.example.ttsaggregator.data

import android.util.Log
import kotlinx.coroutines.flow.first

/**
 * 节点池：按配置的调用逻辑调度多个节点合成。
 *
 * - [CallMode.AUTO]   自动：按健康/延迟顺序尝试，失败自动切换；
 *                     连续失败 3 次 → 冷却 10 分钟（期间不参与调度）
 * - [CallMode.GROUP]  组：组内轮询，每次成功后游标前进（避免单节点被封）
 * - [CallMode.MANUAL] 手选：只用选中节点，连续失败 3 次后停止调用
 *
 * 每次尝试（成功/失败/切换/冷却触发）都会写入 [LogStore]。
 */
class NodePool(
    private val repo: NodeRepository,
    private val settings: SettingsStore,
    private val logs: LogStore,
    private val api: TtsApiClient,
) {

    val config get() = settings.poolConfig.value

    /** 入口：所有朗读（首页 HTTP 朗读、系统 TTS）都走这里 */
    suspend fun synthesize(text: String, prefs: SynthPrefs = SynthPrefs()): Result<ByteArray> {
        val nodes = repo.nodes.first()
        if (nodes.isEmpty()) {
            return Result.failure(IllegalStateException("还没有节点：请先到 设置 → 节点管理 添加"))
        }
        return when (config.mode) {
            CallMode.AUTO -> auto(nodes, text, prefs)
            CallMode.GROUP -> group(nodes, text, prefs)
            CallMode.MANUAL -> manual(nodes, text, prefs)
        }
    }

    /**
     * 执行一次节点合成并记录日志。
     * @param fromNode 失败链上游节点（用于日志展示切换来源）
     */
    private suspend fun attempt(
        node: TtsNode,
        text: String,
        prefs: SynthPrefs,
        mode: CallMode,
        attemptNo: Int,
        fromNode: String? = null,
    ): Result<ByteArray> {
        val t0 = System.currentTimeMillis()
        val bytes = api.synthesize(node, text, prefs, retries = SYNTH_RETRIES_PER_NODE)
        val ms = System.currentTimeMillis() - t0
        val r = if (bytes != null) Result.success(bytes)
        else Result.failure(IllegalStateException("合成失败：${node.baseUrl}（v1/兼容端点均不可用）"))

        r.onSuccess {
            // 成功：清零失败计数与冷却
            if (node.failCount > 0 || node.cooldownUntil > 0) {
                repo.upsert(node.copy(failCount = 0, cooldownUntil = 0))
            }
            logs.insert(
                CallLog(
                    nodeUrl = node.baseUrl, mode = mode, textLen = text.length,
                    attempt = attemptNo, success = true, latencyMs = ms, fromNode = fromNode,
                    textPreview = text.take(TEXT_PREVIEW_LEN),
                    voice = prefs.voice.ifBlank { node.defaultVoice },
                    rate = prefs.rate, pitch = prefs.pitch,
                ),
            )
            Log.i(TAG, "ok ${node.baseUrl} ${ms}ms attempt=$attemptNo")
        }
        r.onFailure {
            val err = it.message ?: it.javaClass.simpleName
            val failCount = node.failCount + 1
            val triggered = failCount >= COOLDOWN_AFTER && mode != CallMode.MANUAL
            val cooldownUntil = if (triggered) System.currentTimeMillis() + COOLDOWN_MS else node.cooldownUntil
            repo.upsert(node.copy(failCount = failCount, cooldownUntil = cooldownUntil))
            logs.insert(
                CallLog(
                    nodeUrl = node.baseUrl, mode = mode, textLen = text.length,
                    attempt = attemptNo, success = false, latencyMs = ms,
                    fromNode = fromNode, error = err, triggeredCooldown = triggered,
                    textPreview = text.take(TEXT_PREVIEW_LEN),
                    voice = prefs.voice.ifBlank { node.defaultVoice },
                    rate = prefs.rate, pitch = prefs.pitch,
                ),
            )
            Log.w(TAG, "fail ${node.baseUrl} ${ms}ms: $err")
        }
        return r
    }

    // ---------- 自动：失败切换下一个 ----------

    private suspend fun auto(
        nodes: List<TtsNode>,
        text: String,
        prefs: SynthPrefs,
    ): Result<ByteArray> {
        val now = System.currentTimeMillis()
        val candidates = nodes
            .filter { it.health != TtsNode.Health.FAIL && !it.disabled && !it.isCoolingDown(now) }
            .sortedWith(compareBy({ it.health.ordinal }, { -it.lastChecked }))
        if (candidates.isEmpty()) {
            val cooling = nodes.count { it.isCoolingDown(now) }
            val msg = if (cooling > 0) "所有节点均不可用（$cooling 个冷却中）" else "没有可用节点"
            return Result.failure(IllegalStateException("$msg，请到节点管理重新测试"))
        }

        var prevFailed: String? = null
        candidates.forEachIndexed { idx, node ->
            val r = attempt(node, text, prefs, CallMode.AUTO, attemptNo = idx + 1, fromNode = prevFailed)
            r.onSuccess { return Result.success(it) }
            prevFailed = node.baseUrl
        }
        return Result.failure(
            IllegalStateException("全部 ${candidates.size} 个节点调用失败，详见调用日志"),
        )
    }

    // ---------- 组：成功后轮换下一个 ----------

    private suspend fun group(
        nodes: List<TtsNode>,
        text: String,
        prefs: SynthPrefs,
    ): Result<ByteArray> {
        val cfg = config
        val now = System.currentTimeMillis()
        val group = nodes.filter { it.id in cfg.groupIds && !it.isCoolingDown(now) }
        if (group.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    if (cfg.groupIds.isEmpty()) "组内没有节点，请在 节点列表 勾选组成员"
                    else "组内节点全部冷却/不可用",
                ),
            )
        }
        var prevFailed: String? = null
        for (i in group.indices) {
            val idx = (cfg.groupCursor + i) % group.size
            val node = group[idx]
            val r = attempt(node, text, prefs, CallMode.GROUP, attemptNo = i + 1, fromNode = prevFailed)
            r.onSuccess {
                // 成功 → 游标指向下一个（负载轮换）
                settings.updatePoolConfig { it.copy(groupCursor = (idx + 1) % group.size) }
                return Result.success(it)
            }
            prevFailed = node.baseUrl
        }
        return Result.failure(IllegalStateException("组内 ${group.size} 个节点全部失败，详见调用日志"))
    }

    // ---------- 手选：失败 3 次停止 ----------

    private suspend fun manual(
        nodes: List<TtsNode>,
        text: String,
        prefs: SynthPrefs,
    ): Result<ByteArray> {
        val cfg = config
        if (cfg.manualFailCount >= FAIL_LIMIT) {
            return Result.failure(
                IllegalStateException(
                    "手选节点已连续失败 $FAIL_LIMIT 次，调用已停止。" +
                        "请到节点管理重新测试，或在首页更换手选节点。",
                ),
            )
        }
        val node = nodes.find { it.id == cfg.manualId }
            ?: return Result.failure(IllegalStateException("尚未选择手选节点，请在 节点列表 选择"))

        val r = attempt(node, text, prefs, CallMode.MANUAL, attemptNo = cfg.manualFailCount + 1)
        if (r.isSuccess) {
            if (cfg.manualFailCount != 0) settings.updatePoolConfig { it.copy(manualFailCount = 0) }
            return Result.success(r.getOrNull()!!)
        }

        val fails = cfg.manualFailCount + 1
        settings.updatePoolConfig { it.copy(manualFailCount = fails) }
        val err = r.exceptionOrNull()?.message ?: "未知错误"
        return if (fails >= FAIL_LIMIT) {
            Result.failure(
                IllegalStateException("手选节点连续失败 $FAIL_LIMIT 次，已停止调用：$err"),
            )
        } else {
            Result.failure(
                IllegalStateException("手选节点第 $fails 次失败（剩余 ${FAIL_LIMIT - fails} 次机会）：$err"),
            )
        }
    }

    companion object {
        private const val TAG = "TTS-Agg"
        private const val FAIL_LIMIT = 3
        private const val COOLDOWN_AFTER = 3        // 连续失败 3 次进入冷却
        private const val COOLDOWN_MS = 10 * 60 * 1000L
        private const val TEXT_PREVIEW_LEN = 80     // 日志记录的文本预览长度
        private const val SYNTH_RETRIES_PER_NODE = 1 // 池内每个节点只试一次（切换即重试）
    }
}
