package com.pulse.tts.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 网络不可用时的统一文案：明确说明没有切换节点、没有计入冷却。 */
const val NETWORK_UNAVAILABLE_MESSAGE = "当前网络不可用，已暂停节点切换（未计入冷却）"

/** 网络可用性抽象：调度层只依赖这个接口，测试可注入固定值。 */
fun interface NetworkState {
    fun isOnline(): Boolean
}

/**
 * 严格网络判定：必须同时具备 INTERNET 与 VALIDATED。
 *
 * 连上 WiFi 但没有外网时 VALIDATED 为 false，此时不应切换节点，
 * 否则会把一次断网放大成所有节点连续失败并进入冷却。
 */
internal fun isUsableNetwork(hasInternet: Boolean, validated: Boolean): Boolean =
    hasInternet && validated

/** 监听系统默认网络，供调度层与 UI 共享在线状态。 */
class NetworkMonitor(context: Context) : NetworkState {

    private val manager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(checkNow())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()

        override fun onLost(network: Network) = refresh()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refresh()

        override fun onUnavailable() = refresh()
    }

    init {
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
    }

    override fun isOnline(): Boolean = checkNow()

    fun checkNow(): Boolean {
        val cm = manager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return isUsableNetwork(
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    private fun refresh() {
        _online.value = checkNow()
    }
}
