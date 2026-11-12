package com.pulse.tts.viewmodel

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.tts.data.NodeRepository
import com.pulse.tts.data.lansync.CONFIRM_TIMEOUT_MS
import com.pulse.tts.data.lansync.IncomingRequest
import com.pulse.tts.data.lansync.LanSyncEntry
import com.pulse.tts.data.lansync.MdnsDiscovery
import com.pulse.tts.data.lansync.MergeStats
import com.pulse.tts.data.lansync.PeerInfo
import com.pulse.tts.data.lansync.SyncClient
import com.pulse.tts.data.lansync.SyncDecision
import com.pulse.tts.data.lansync.SyncOutcome
import com.pulse.tts.data.lansync.SyncPeer
import com.pulse.tts.data.lansync.SyncServer
import com.pulse.tts.data.lansync.SyncedNode
import com.pulse.tts.data.lansync.buildPairingString
import com.pulse.tts.data.lansync.parsePairingString
import com.pulse.tts.data.lansync.toSyncedNodes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class LanSyncPhase { IDLE, RUNNING, SYNCING, DONE, ERROR }

data class PendingSync(
    val requestId: String,
    val fromName: String,
    val nodes: List<SyncedNode>,
)

data class LanSyncState(
    val phase: LanSyncPhase = LanSyncPhase.IDLE,
    val port: Int = 0,
    val sid: String = "",
    val localIp: String = "",
    val pairing: String = "",
    val pending: PendingSync? = null,
    val stats: MergeStats? = null,
    val message: String? = null,
)

class LanSyncViewModel(
    private val context: Context,
    private val repo: NodeRepository,
) : ViewModel() {

    private val discovery = MdnsDiscovery(context)
    val peers: StateFlow<List<SyncPeer>> = discovery.peers

    private val client = SyncClient()
    private var server: SyncServer? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var decision: CompletableDeferred<Boolean>? = null

    private val _state = MutableStateFlow(LanSyncState())
    val state: StateFlow<LanSyncState> = _state

    val deviceName: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()

    fun start() {
        if (server != null) return
        val sid = UUID.randomUUID().toString().replace("-", "")
        val lock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("pulse-lansync")
            .apply { setReferenceCounted(false); acquire() }
        multicast = lock
        val instance = SyncServer(
            deviceName = deviceName,
            sid = sid,
            localNodes = { toSyncedNodes(repo.nodes.value) },
            confirm = ::askUser,
        )
        val port = runCatching { instance.start() }.getOrElse { error ->
            lock.release()
            multicast = null
            _state.value = LanSyncState(
                phase = LanSyncPhase.ERROR,
                message = "无法启动同步服务：${error.message}",
            )
            return
        }
        server = instance
        discovery.start()
        val ip = localIpv4()
        _state.value = LanSyncState(
            phase = LanSyncPhase.RUNNING,
            port = port,
            sid = sid,
            localIp = ip,
            pairing = buildPairingString(SyncPeer(ip, port, sid, deviceName, "android")),
        )
    }

    fun stop() {
        decision?.complete(false)
        decision = null
        server?.stop()
        server = null
        discovery.stop()
        multicast?.takeIf { it.isHeld }?.release()
        multicast = null
        _state.value = LanSyncState()
    }

    fun syncTo(peer: SyncPeer) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(phase = LanSyncPhase.SYNCING, message = null, stats = null) }
            val outcome = client.sync(
                peer,
                PeerInfo(deviceName, "android"),
                toSyncedNodes(repo.nodes.value),
            )
            when (outcome) {
                is SyncOutcome.Ok -> {
                    val stats = repo.mergeSynced(outcome.response.nodes)
                    _state.update { it.copy(phase = LanSyncPhase.DONE, stats = stats) }
                }
                SyncOutcome.Rejected -> _state.update {
                    it.copy(phase = LanSyncPhase.ERROR, message = "对方拒绝了本次同步")
                }
                SyncOutcome.Timeout -> _state.update {
                    it.copy(phase = LanSyncPhase.ERROR, message = "对方 60 秒内未确认")
                }
                is SyncOutcome.Failed -> _state.update {
                    it.copy(phase = LanSyncPhase.ERROR, message = "无法连接到设备，请确认在同一 Wi-Fi")
                }
            }
        }
    }

    /** 深链或粘贴的配对串：解析后直接发起同步。 */
    fun syncFromString(raw: String) {
        val peer = parsePairingString(raw)
        if (peer == null) {
            _state.update { it.copy(phase = LanSyncPhase.ERROR, message = "配对串无效") }
            return
        }
        syncTo(peer)
    }

    fun accept() {
        decision?.complete(true)
    }

    fun reject() {
        decision?.complete(false)
    }

    fun consumeDeepLink() {
        LanSyncEntry.consume()
    }

    private suspend fun askUser(request: IncomingRequest): SyncDecision? {
        val deferred = CompletableDeferred<Boolean>()
        decision = deferred
        _state.update {
            it.copy(
                phase = LanSyncPhase.RUNNING,
                pending = PendingSync(UUID.randomUUID().toString(), request.from.name, request.nodes),
            )
        }
        val accepted = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() }
        decision = null
        _state.update { it.copy(pending = null) }
        if (accepted == null) return null
        if (!accepted) return SyncDecision.Reject
        val stats = repo.mergeSynced(request.nodes)
        _state.update { it.copy(phase = LanSyncPhase.DONE, stats = stats) }
        return SyncDecision.Accept(toSyncedNodes(repo.nodes.value), stats.total)
    }

    private fun localIpv4(): String = runCatching {
        java.net.DatagramSocket().use { socket ->
            socket.connect(java.net.InetAddress.getByName("223.5.5.5"), 80)
            socket.localAddress.hostAddress
        }
    }.getOrNull().orEmpty()

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
