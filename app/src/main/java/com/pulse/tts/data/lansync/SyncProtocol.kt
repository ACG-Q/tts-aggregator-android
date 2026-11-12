package com.pulse.tts.data.lansync

import com.pulse.tts.data.ApiVersion
import com.pulse.tts.data.TtsNode
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

const val PROTOCOL_VERSION = 1
const val MAX_NODES = 500
const val MAX_BODY_BYTES = 1024 * 1024
const val CONFIRM_TIMEOUT_MS = 60_000L

@Serializable
data class SyncedNode(
    val url: String,
    val label: String = "",
    val health: String = "UNKNOWN",
    val apiVersion: String = "UNKNOWN",
    val defaultVoice: String = "",
    val voiceCount: Int = 0,
    val group: String? = null,
    val keyless: Boolean? = null,
)

@Serializable
data class PeerInfo(val name: String, val platform: String)

@Serializable
data class SyncRequest(val from: PeerInfo, val nodes: List<SyncedNode> = emptyList())

@Serializable
data class SyncResponse(val nodes: List<SyncedNode> = emptyList(), val applied: Int = 0)

@Serializable
data class PingResponse(
    val name: String,
    val platform: String = "android",
    val v: Int = PROTOCOL_VERSION,
    val nodes: Int = 0,
)

@Serializable
data class ErrorBody(val reason: String)

data class SyncPeer(
    val host: String,
    val port: Int,
    val sid: String,
    val name: String,
    val platform: String,
)

data class MergeStats(val added: Int, val updated: Int, val skipped: Int, val total: Int)

fun normalizeUrl(raw: String): String? {
    val text = raw.trim()
    if (!text.startsWith("http://", ignoreCase = true) &&
        !text.startsWith("https://", ignoreCase = true)
    ) return null
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val defaultPort = if (scheme == "http") 80 else 443
    val portPart = if (uri.port > 0 && uri.port != defaultPort) ":${uri.port}" else ""
    val path = uri.path.orEmpty().trimEnd('/')
    return "$scheme://$host$portPart$path"
}

fun toSyncedNodes(nodes: List<TtsNode>): List<SyncedNode> = nodes.map { node ->
    SyncedNode(
        url = node.baseUrl,
        label = node.label,
        health = node.health.name,
        apiVersion = node.apiVersion.name,
        defaultVoice = node.defaultVoice,
        voiceCount = node.voiceCount,
        group = node.group,
        keyless = node.keyless,
    )
}

fun mergeSyncedNodes(local: List<TtsNode>, remote: List<SyncedNode>): Pair<List<TtsNode>, MergeStats> {
    val byUrl = local.associateBy { normalizeUrl(it.baseUrl) ?: it.baseUrl }.toMutableMap()
    val result = local.toMutableList()
    val remoteMap = LinkedHashMap<String, SyncedNode>()
    var skipped = 0
    for (item in remote) {
        val key = normalizeUrl(item.url)
        if (key == null) {
            skipped++
            continue
        }
        remoteMap[key] = item
    }

    var added = 0
    var updated = 0
    for ((key, item) in remoteMap) {
        val existing = byUrl[key]
        if (existing != null) {
            val index = result.indexOfFirst { it.id == existing.id }
            result[index] = existing.copy(
                label = item.label,
                health = healthOf(item.health),
                apiVersion = apiVersionOf(item.apiVersion),
                defaultVoice = item.defaultVoice.ifBlank { existing.defaultVoice },
                voiceCount = item.voiceCount,
            )
            updated++
            continue
        }
        val fresh = TtsNode(
            baseUrl = key,
            label = item.label,
            keyless = item.keyless ?: false,
            apiKey = null,
            health = healthOf(item.health),
            defaultVoice = item.defaultVoice,
            voiceCount = item.voiceCount,
            group = item.group?.ifBlank { "默认" } ?: "默认",
            apiVersion = apiVersionOf(item.apiVersion),
        )
        result += fresh
        byUrl[key] = fresh
        added++
    }

    return result to MergeStats(added, updated, skipped, added + updated)
}

internal fun healthOf(raw: String?): TtsNode.Health = when (raw?.uppercase()) {
    "OK" -> TtsNode.Health.OK
    "FAIL" -> TtsNode.Health.FAIL
    else -> TtsNode.Health.UNKNOWN
}

internal fun apiVersionOf(raw: String?): ApiVersion = when (raw?.uppercase()) {
    "V1" -> ApiVersion.V1
    "LEGACY" -> ApiVersion.LEGACY
    else -> ApiVersion.UNKNOWN
}

fun buildPairingString(peer: SyncPeer): String {
    val params = linkedMapOf(
        "host" to peer.host,
        "port" to peer.port.toString(),
        "sid" to peer.sid,
        "name" to peer.name,
        "platform" to peer.platform,
    )
    val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    return "pulsetts://sync?$query"
}

fun parsePairingString(raw: String): SyncPeer? {
    val text = raw.trim()
    if (!text.startsWith("pulsetts://sync", ignoreCase = true)) return null
    val query = text.substringAfter('?', "")
    if (query.isEmpty()) return null
    val params = query.split('&').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) null
        else part.substring(0, index) to URLDecoder.decode(part.substring(index + 1), "UTF-8")
    }.toMap()
    val host = params["host"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val sid = params["sid"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    val name = params["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: "对端设备"
    val platform = if (params["platform"] == "desktop") "desktop" else "android"
    return SyncPeer(host, port, sid, name, platform)
}
