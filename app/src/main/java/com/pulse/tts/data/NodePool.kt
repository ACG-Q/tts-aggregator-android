package com.pulse.tts.data

import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val network: NetworkState,
    private val cache: AudioCache,
) {

    private val health = NodeHealthRegistry(
        read = repo::get,
        write = repo::upsert,
        failThreshold = { settings.appSettings.value.failThreshold },
        cooldownMs = { settings.appSettings.value.cooldownMinutes * 60_000L },
    )

    val config get() = settings.poolConfig.value

    /** 入口：所有朗读（首页 HTTP 朗读、系统 TTS）都走这里 */
    suspend fun synthesize(
        text: String,
        prefs: SynthPrefs = SynthPrefs(),
        onEvent: (SynthesisEvent) -> Unit = {},
    ): Result<ByteArray> {
        // 先查缓存再判断网络：断网时重复朗读同一段话依然能播放
        cache.get(text, prefs)?.let { return Result.success(it) }
        repo.awaitReady()
        if (!network.isOnline()) {
            onEvent(SynthesisEvent.Offline)
            return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
        }
        val nodes = repo.nodes.first()
        if (nodes.isEmpty()) {
            val message = "还没有节点：请先到 设置 → 节点管理 添加"
            onEvent(SynthesisEvent.Failed(message))
            return Result.failure(IllegalStateException(message))
        }
        return when (config.mode) {
            CallMode.AUTO -> auto(nodes, text, prefs, onEvent)
            CallMode.GROUP -> group(nodes, text, prefs, onEvent)
            CallMode.MANUAL -> manual(nodes, text, prefs, onEvent)
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
        // 已取消（停止朗读 / 被新请求顶掉）时不再发请求，也不记节点失败
        currentCoroutineContext().ensureActive()
        if (!network.isOnline()) {
            return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
        }
        val t0 = System.currentTimeMillis()
        val bytes = api.synthesize(node, text, prefs, retries = SYNTH_RETRIES_PER_NODE)
        val ms = System.currentTimeMillis() - t0
        val r = if (bytes != null) Result.success(bytes)
        else Result.failure(IllegalStateException("合成失败：${node.baseUrl}（v1/兼容端点均不可用）"))

        r.onSuccess {
            // 成功：清零失败计数与冷却（锁内读最新值）
            health.onSuccess(node)
            cache.put(text, prefs, it)
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
            val online = network.isOnline()
            val err = if (online) it.message ?: it.javaClass.simpleName else NETWORK_UNAVAILABLE_MESSAGE
            val state = if (online) {
                health.onFailure(node, mode)
            } else {
                AttemptState(failCount = 0, cooldownUntil = 0, triggeredCooldown = false)
            }
            logs.insert(
                CallLog(
                    nodeUrl = node.baseUrl, mode = mode, textLen = text.length,
                    attempt = attemptNo, success = false, latencyMs = ms,
                    fromNode = fromNode, error = err,
                    triggeredCooldown = state.triggeredCooldown,
                    textPreview = text.take(TEXT_PREVIEW_LEN),
                    voice = prefs.voice.ifBlank { node.defaultVoice },
                    rate = prefs.rate, pitch = prefs.pitch,
                ),
            )
            Log.w(TAG, "fail ${node.baseUrl} ${ms}ms: $err")
        }
        return if (network.isOnline()) r
        else Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
    }

    // ---------- 自动：失败切换下一个 ----------

    private suspend fun auto(
        nodes: List<TtsNode>,
        text: String,
        prefs: SynthPrefs,
        onEvent: (SynthesisEvent) -> Unit,
    ): Result<ByteArray> {
        if (!network.isOnline()) {
            onEvent(SynthesisEvent.Offline)
            return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
        }
        val now = System.currentTimeMillis()
        val candidates = selectAutoCandidates(nodes, now)
        if (candidates.isEmpty()) {
            val cooling = nodes.count { it.isCoolingDown(now) }
            val msg = if (cooling > 0) "所有节点均不可用（$cooling 个冷却中）" else "没有可用节点"
            val message = "$msg，请到节点管理重新测试"
            onEvent(SynthesisEvent.Failed(message, CallMode.AUTO))
            return Result.failure(IllegalStateException(message))
        }

        var prevFailed: String? = null
        candidates.forEachIndexed { idx, node ->
            if (!network.isOnline()) {
                onEvent(SynthesisEvent.Offline)
                return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
            }
            onEvent(
                SynthesisEvent.AttemptStarted(
                    node = node,
                    attempt = idx + 1,
                    total = candidates.size,
                    mode = CallMode.AUTO,
                    fromNode = prevFailed,
                ),
            )
            val t0 = System.currentTimeMillis()
            val r = attempt(node, text, prefs, CallMode.AUTO, attemptNo = idx + 1, fromNode = prevFailed)
            val elapsed = System.currentTimeMillis() - t0
            r.onSuccess {
                onEvent(SynthesisEvent.Succeeded(node, elapsed, CallMode.AUTO))
                return Result.success(it)
            }
            if (!network.isOnline()) {
                onEvent(SynthesisEvent.Offline)
                return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
            }
            onEvent(
                SynthesisEvent.AttemptFailed(
                    node = node,
                    error = r.exceptionOrNull()?.message ?: "未知错误",
                    willRetry = idx < candidates.lastIndex,
                    mode = CallMode.AUTO,
                ),
            )
            prevFailed = node.baseUrl
        }
        val message = "全部 ${candidates.size} 个节点调用失败，详见调用日志"
        onEvent(SynthesisEvent.Failed(message, CallMode.AUTO))
        return Result.failure(IllegalStateException(message))
    }

    // ---------- 组：成功后轮换下一个 ----------

    private suspend fun group(
        nodes: List<TtsNode>,
        text: String,
        prefs: SynthPrefs,
        onEvent: (SynthesisEvent) -> Unit,
    ): Result<ByteArray> {
        if (!network.isOnline()) {
            onEvent(SynthesisEvent.Offline)
            return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
        }
        val cfg = config
        val now = System.currentTimeMillis()
        // 与自动模式一致：停用节点不参与调度
        val group = selectGroupCandidates(nodes, cfg.groupIds, now)
        if (group.isEmpty()) {
            val msg = when {
                cfg.groupIds.isEmpty() -> "组内没有节点，请在 节点列表 勾选组成员"
                else -> "组内节点全部停用/冷却，请在 节点列表 调整"
            }
            onEvent(SynthesisEvent.Failed(msg, CallMode.GROUP))
            return Result.failure(IllegalStateException(msg))
        }
        var prevFailed: String? = null
        for (i in group.indices) {
            if (!network.isOnline()) {
                onEvent(SynthesisEvent.Offline)
                return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
            }
            val idx = (cfg.groupCursor + i) % group.size
            val node = group[idx]
            onEvent(
                SynthesisEvent.AttemptStarted(
                    node = node,
                    attempt = i + 1,
                    total = group.size,
                    mode = CallMode.GROUP,
                    fromNode = prevFailed,
                ),
            )
            val t0 = System.currentTimeMillis()
            val r = attempt(node, text, prefs, CallMode.GROUP, attemptNo = i + 1, fromNode = prevFailed)
            val elapsed = System.currentTimeMillis() - t0
            r.onSuccess {
                onEvent(SynthesisEvent.Succeeded(node, elapsed, CallMode.GROUP))
                // 成功 → 游标指向下一个（负载轮换）
                settings.updatePoolConfig { it.copy(groupCursor = (idx + 1) % group.size) }
                return Result.success(it)
            }
            if (!network.isOnline()) {
                onEvent(SynthesisEvent.Offline)
                return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
            }
            onEvent(
                SynthesisEvent.AttemptFailed(
                    node = node,
                    error = r.exceptionOrNull()?.message ?: "未知错误",
                    willRetry = i < group.lastIndex,
                    mode = CallMode.GROUP,
                ),
            )
            prevFailed = node.baseUrl
        }
        val message = "组内 ${group.size} 个节点全部失败，详见调用日志"
        onEvent(SynthesisEvent.Failed(message, CallMode.GROUP))
        return Result.failure(IllegalStateException(message))
    }

    // ---------- 手选：失败 3 次停止 ----------

    private suspend fun manual(
        nodes: List<TtsNode>,
        text: String,
        prefs: SynthPrefs,
        onEvent: (SynthesisEvent) -> Unit,
    ): Result<ByteArray> {
        if (!network.isOnline()) {
            onEvent(SynthesisEvent.Offline)
            return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
        }
        val cfg = config
        if (cfg.manualFailCount >= FAIL_LIMIT) {
            val message = "手选节点已连续失败 $FAIL_LIMIT 次，调用已停止。" +
                "请到节点管理重新测试，或在首页更换手选节点。"
            onEvent(SynthesisEvent.Failed(message, CallMode.MANUAL))
            return Result.failure(IllegalStateException(message))
        }
        val node = nodes.find { it.id == cfg.manualId }
            ?: run {
                val message = "尚未选择手选节点，请在 节点列表 选择"
                onEvent(SynthesisEvent.Failed(message, CallMode.MANUAL))
                return Result.failure(IllegalStateException(message))
            }

        onEvent(
            SynthesisEvent.AttemptStarted(
                node = node,
                attempt = cfg.manualFailCount + 1,
                total = 1,
                mode = CallMode.MANUAL,
            ),
        )
        val t0 = System.currentTimeMillis()
        val r = attempt(node, text, prefs, CallMode.MANUAL, attemptNo = cfg.manualFailCount + 1)
        val elapsed = System.currentTimeMillis() - t0
        if (r.isSuccess) {
            onEvent(SynthesisEvent.Succeeded(node, elapsed, CallMode.MANUAL))
            if (cfg.manualFailCount != 0) settings.updatePoolConfig { it.copy(manualFailCount = 0) }
            return Result.success(r.getOrNull()!!)
        }

        if (!network.isOnline()) {
            onEvent(SynthesisEvent.Offline)
            return Result.failure(IllegalStateException(NETWORK_UNAVAILABLE_MESSAGE))
        }

        val fails = cfg.manualFailCount + 1
        settings.updatePoolConfig { it.copy(manualFailCount = fails) }
        val err = r.exceptionOrNull()?.message ?: "未知错误"
        val willRetry = fails < FAIL_LIMIT
        onEvent(
            SynthesisEvent.AttemptFailed(
                node = node,
                error = err,
                willRetry = willRetry,
                mode = CallMode.MANUAL,
            ),
        )
        return if (!willRetry) {
            val message = "手选节点连续失败 $FAIL_LIMIT 次，已停止调用：$err"
            onEvent(SynthesisEvent.Failed(message, CallMode.MANUAL))
            Result.failure(IllegalStateException(message))
        } else {
            Result.failure(
                IllegalStateException("手选节点第 $fails 次失败（剩余 ${FAIL_LIMIT - fails} 次机会）：$err"),
            )
        }
    }

    companion object {
        private const val TAG = "TTS-Agg"
        private const val FAIL_LIMIT = 3
        private const val TEXT_PREVIEW_LEN = 80     // 日志记录的文本预览长度
        private const val SYNTH_RETRIES_PER_NODE = 1 // 池内每个节点只试一次（切换即重试）
    }
}

/**
 * 自动模式候选：排除异常/停用/冷却节点，健康优先、最近测试在前。
 * 抽成纯函数便于单测（无 Android 依赖）。
 */
internal fun selectAutoCandidates(nodes: List<TtsNode>, now: Long): List<TtsNode> = nodes
    .filter { it.health != TtsNode.Health.FAIL && !it.disabled && !it.isCoolingDown(now) }
    .sortedWith(compareBy({ it.health.ordinal }, { -it.lastChecked }))

/**
 * 组模式候选：只保留组内、未停用、未冷却的节点，保持原有顺序（轮询游标依赖它）。
 */
internal fun selectGroupCandidates(
    nodes: List<TtsNode>,
    groupIds: Collection<String>,
    now: Long,
): List<TtsNode> = nodes.filter { it.id in groupIds && !it.disabled && !it.isCoolingDown(now) }
