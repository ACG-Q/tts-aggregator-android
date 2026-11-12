package com.pulse.tts.data.lansync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * `_pulse-tts._tcp` 发现：优先读取 onServiceFound 的 TXT，
 * 缺失时回退到已弃用但仍可用的 resolveService（部分机型不返回 TXT）。
 */
class MdnsDiscovery(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _peers = MutableStateFlow<List<SyncPeer>>(emptyList())
    val peers: StateFlow<List<SyncPeer>> = _peers
    private var listener: NsdManager.DiscoveryListener? = null

    @Suppress("DEPRECATION")
    fun start() {
        if (listener != null) return
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) = Unit
            override fun onDiscoveryStopped(serviceType: String?) = Unit
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = stop()
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = stop()

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                val name = serviceInfo?.serviceName ?: return
                _peers.value = _peers.value.filterNot { it.name == name }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val info = serviceInfo ?: return
                if (info.serviceType != SERVICE_TYPE) return
                val sid = info.text("sid")
                val host = info.host?.hostAddress
                if (sid != null && info.port > 0 && host != null) {
                    add(
                        SyncPeer(
                            host = host,
                            port = info.port,
                            sid = sid,
                            name = info.text("name") ?: info.serviceName,
                            platform = info.text("platform") ?: "desktop",
                        ),
                    )
                } else {
                    resolve(info)
                }
            }
        }
        listener = discovery
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        runCatching { nsd.stopServiceDiscovery(current) }
        _peers.value = emptyList()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit

            override fun onServiceResolved(resolved: NsdServiceInfo?) {
                val found = resolved ?: return
                val host = found.host?.hostAddress ?: return
                val sid = found.text("sid") ?: return
                add(
                    SyncPeer(
                        host = host,
                        port = found.port,
                        sid = sid,
                        name = found.text("name") ?: found.serviceName,
                        platform = found.text("platform") ?: "desktop",
                    ),
                )
            }
        }
        runCatching { nsd.resolveService(info, resolveListener) }
    }

    private fun add(peer: SyncPeer) {
        _peers.value = _peers.value.filterNot { it.sid == peer.sid } + peer
    }

    private fun NsdServiceInfo.text(key: String): String? =
        attributes?.get(key)?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }

    companion object {
        const val SERVICE_TYPE = "_pulse-tts._tcp."
    }
}

/** 深链配对串的进程内投递：MainActivity 写入，导航与页面消费。 */
object LanSyncEntry {
    private val _pairing = MutableStateFlow<String?>(null)
    val pairing: StateFlow<String?> = _pairing

    fun offer(raw: String) {
        _pairing.value = raw
    }

    fun consume(): String? = _pairing.value?.also { _pairing.value = null }
}
