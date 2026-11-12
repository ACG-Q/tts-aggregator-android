# 安卓端局域网同步实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在安卓端新增「局域网同步」：`NsdManager` 发现桌面端，按需启动本地 HTTP 服务接收同步，通过一次往返双向合并节点。

**架构：** `data/lansync` 包内含协议纯函数、`ServerSocket` 服务端、OkHttp 客户端与 mDNS 发现；`LanSyncViewModel` 编排状态与确认流程；`LanSyncScreen` 提供独立页面，设置页只加入口。

**技术栈：** Kotlin 2.0、Compose、kotlinx-serialization、OkHttp 4.12、Android `NsdManager`（minSdk 26）、JUnit4。

**范围说明：** 协议与桌面端见 `tts-aggregator-desktop/docs/superpowers/specs/2026-11-10-lan-sync-design.md`。本计划只改 `tts-aggregator-android/`。

**工作目录：** 所有命令均在 `tts-aggregator-android/` 下执行。

---

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/pulse/tts/data/lansync/SyncProtocol.kt` | 协议模型、URL 规范化、合并纯函数、配对串 |
| `app/src/main/java/com/pulse/tts/data/lansync/SyncServer.kt` | 按需 HTTP 服务端 |
| `app/src/main/java/com/pulse/tts/data/lansync/SyncClient.kt` | OkHttp 出站 |
| `app/src/main/java/com/pulse/tts/data/lansync/MdnsDiscovery.kt` | `NsdManager` 发现 |
| `app/src/main/java/com/pulse/tts/data/lansync/LanSyncEntry.kt` | 深链配对串的进程内投递 |
| `app/src/main/java/com/pulse/tts/viewmodel/LanSyncViewModel.kt` | 状态机、确认、合并编排 |
| `app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt` | 同步页面 |
| `app/src/test/java/com/pulse/tts/data/lansync/SyncProtocolTest.kt` | 纯函数单测 |
| `app/src/test/java/com/pulse/tts/data/lansync/SyncServerTest.kt` | 服务端集成测试 |

---

### 任务 1：协议纯函数

**文件：**
- 创建：`app/src/main/java/com/pulse/tts/data/lansync/SyncProtocol.kt`
- 创建：`app/src/test/java/com/pulse/tts/data/lansync/SyncProtocolTest.kt`

- [ ] **步骤 1：写失败测试**

```kotlin
package com.pulse.tts.data.lansync

import com.pulse.tts.data.TtsNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncProtocolTest {

    @Test
    fun `normalize drops default port and trailing slash`() {
        assertEquals("https://a.example.com", normalizeUrl("https://A.example.com:443/"))
        assertEquals("http://a.example.com/tts", normalizeUrl("http://a.example.com:80/tts/"))
        assertEquals("http://a.example.com:8080", normalizeUrl("http://a.example.com:8080/"))
    }

    @Test
    fun `normalize rejects non http`() {
        assertNull(normalizeUrl("ftp://a.example.com"))
        assertNull(normalizeUrl("a.example.com"))
        assertNull(normalizeUrl("   "))
    }

    @Test
    fun `merge adds new nodes without api key and keeps local secrets`() {
        val local = listOf(
            TtsNode(baseUrl = "https://a.example.com", label = "旧", apiKey = "secret",
                disabled = true, failCount = 2, group = "工作"),
        )
        val remote = listOf(
            SyncedNode(url = "https://A.example.com/", label = "新", health = "FAIL", voiceCount = 9),
            SyncedNode(url = "https://b.example.com", label = "B", group = "家里", keyless = true),
            SyncedNode(url = "ftp://bad"),
        )
        val (nodes, stats) = mergeSyncedNodes(local, remote)

        assertEquals(MergeStats(added = 1, updated = 1, skipped = 1, total = 2), stats)
        val a = nodes.first { it.baseUrl == "https://a.example.com" }
        assertEquals("新", a.label)
        assertEquals("secret", a.apiKey)
        assertEquals(true, a.disabled)
        assertEquals(2, a.failCount)
        assertEquals("工作", a.group)
        val b = nodes.first { it.baseUrl == "https://b.example.com" }
        assertNull(b.apiKey)
        assertEquals("家里", b.group)
        assertEquals(true, b.keyless)
    }

    @Test
    fun `merge keeps local-only nodes`() {
        val local = listOf(TtsNode(baseUrl = "https://keep.example.com", id = "keep"))
        val (nodes, stats) = mergeSyncedNodes(local, emptyList())
        assertEquals(1, nodes.size)
        assertEquals("keep", nodes[0].id)
        assertEquals(MergeStats(0, 0, 0, 0), stats)
    }

    @Test
    fun `toSyncedNodes omits api key`() {
        val node = TtsNode(baseUrl = "https://a.example.com", apiKey = "secret", label = "A")
        val synced = toSyncedNodes(listOf(node)).single()
        assertEquals("https://a.example.com", synced.url)
        assertEquals("默认", synced.group)
        assertEquals("A", synced.label)
    }

    @Test
    fun `pairing string round trips`() {
        val peer = SyncPeer("192.168.1.20", 53421, "ab12", "书房的电脑", "desktop")
        assertEquals(peer, parsePairingString(buildPairingString(peer)))
    }

    @Test
    fun `pairing string rejects invalid input`() {
        assertNull(parsePairingString("https://x"))
        assertNull(parsePairingString("pulsetts://sync?host=a&port=0&sid=x"))
        assertNull(parsePairingString("pulsetts://sync?host=a&port=1"))
    }
}
```

- [ ] **步骤 2：运行，确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pulse.tts.data.lansync.SyncProtocolTest"
```

（Windows：`gradlew.bat :app:testDebugUnitTest --tests "com.pulse.tts.data.lansync.SyncProtocolTest"`）

预期：编译失败，`Unresolved reference: normalizeUrl`。

- [ ] **步骤 3：实现 `SyncProtocol.kt`**

```kotlin
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
```

> `group` / `keyless` 为空时取默认值，避免把桌面端的 `null` 写进本地节点。

- [ ] **步骤 4：运行测试**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pulse.tts.data.lansync.SyncProtocolTest"
```

预期：7 个测试全部 PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/pulse/tts/data/lansync app/src/test/java/com/pulse/tts/data/lansync
git commit -m "feat(lan-sync): 协议模型与合并纯函数"
```

---

### 任务 2：本地 HTTP 服务端

**文件：**
- 创建：`app/src/main/java/com/pulse/tts/data/lansync/SyncServer.kt`
- 创建：`app/src/test/java/com/pulse/tts/data/lansync/SyncServerTest.kt`

- [ ] **步骤 1：写失败测试**

```kotlin
package com.pulse.tts.data.lansync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class SyncServerTest {

    private fun get(port: Int, path: String): Triple<Int, Map<String, String>, String> {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(
                "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(),
            )
            return readResponse(BufferedReader(InputStreamReader(socket.getInputStream())))
        }
    }

    private fun post(port: Int, path: String, sid: String, body: String): Triple<Int, Map<String, String>, String> {
        Socket("127.0.0.1", port).use { socket ->
            val bytes = body.toByteArray()
            val head = "POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\n" +
                "X-Sync-Session: $sid\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(head.toByteArray() + bytes)
            return readResponse(BufferedReader(InputStreamReader(socket.getInputStream())))
        }
    }

    private fun readResponse(reader: BufferedReader): Triple<Int, Map<String, String>, String> {
        val status = reader.readLine().split(' ')[1].toInt()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val index = line.indexOf(':')
            headers[line.substring(0, index).lowercase()] = line.substring(index + 1).trim()
        }
        return Triple(status, headers, reader.readText())
    }

    @Test
    fun `ping returns peer info`() {
        val server = SyncServer("测试机", "sid-1", { emptyList() }, { null })
        val port = server.start()
        try {
            val (status, _, body) = get(port, "/v1/ping")
            assertEquals(200, status)
            assertTrue(body.contains("\"name\":\"测试机\""))
            assertTrue(body.contains("\"platform\":\"android\""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `sync rejects wrong session`() {
        val server = SyncServer("测试机", "sid-1", { emptyList() }, { null })
        val port = server.start()
        try {
            val (status, _, _) = post(port, "/v1/sync", "wrong",
                """{"from":{"name":"电脑","platform":"desktop"},"nodes":[]}""")
            assertEquals(403, status)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `sync accepts when user confirms`() = runBlocking {
        val offered = listOf(SyncedNode(url = "https://a.example.com", label = "A"))
        val server = SyncServer(
            "测试机", "sid-1", { emptyList() },
            { req -> SyncDecision.Accept(listOf(SyncedNode(url = "https://local.example.com")), req.nodes.size) },
        )
        val port = server.start()
        try {
            val (status, _, body) = post(port, "/v1/sync", "sid-1",
                """{"from":{"name":"电脑","platform":"desktop"},"nodes":[{"url":"https://a.example.com","label":"A"}]}""")
            assertEquals(200, status)
            assertTrue(body.contains("local.example.com"))
        } finally {
            server.stop()
        }
    }
}
```

- [ ] **步骤 2：运行，确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pulse.tts.data.lansync.SyncServerTest"
```

预期：编译失败，`Unresolved reference: SyncServer`。

- [ ] **步骤 3：实现 `SyncServer.kt`**

```kotlin
package com.pulse.tts.data.lansync

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

data class IncomingRequest(val from: PeerInfo, val nodes: List<SyncedNode>)

sealed class SyncDecision {
    data class Accept(val nodes: List<SyncedNode>, val applied: Int) : SyncDecision()
    object Reject : SyncDecision()
}

/**
 * 按需启动的极简 HTTP/1.1 服务端，只处理 ping 与 sync 两个端点。
 * 一次请求一个连接，读完即关，不处理 keep-alive 与分块传输。
 */
class SyncServer(
    private val deviceName: String,
    private val sid: String,
    private val localNodes: () -> List<SyncedNode>,
    private val confirm: suspend (IncomingRequest) -> SyncDecision?,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false
    private var socket: ServerSocket? = null

    fun start(): Int {
        val server = ServerSocket(0)
        socket = server
        running = true
        pool.execute {
            while (running) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(client) } }
            }
        }
        return server.localPort
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        client.use { conn ->
            conn.soTimeout = 10_000
            val reader = conn.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            var session: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val index = line.indexOf(':')
                if (index <= 0) continue
                when (line.substring(0, index).trim().lowercase()) {
                    "content-length" -> contentLength = line.substring(index + 1).trim().toIntOrNull() ?: 0
                    "x-sync-session" -> session = line.substring(index + 1).trim()
                }
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?')

            when {
                method == "GET" && path == "/v1/ping" -> {
                    val body = json.encodeToString(
                        PingResponse.serializer(),
                        PingResponse(name = deviceName, nodes = localNodes().size),
                    )
                    respond(conn, 200, body)
                }
                method == "POST" && path == "/v1/sync" -> handleSync(conn, reader, session, contentLength)
                else -> respond(conn, 404, json.encodeToString(ErrorBody.serializer(), ErrorBody("not_found")))
            }
        }
    }

    private fun handleSync(client: Socket, reader: java.io.Reader, session: String?, contentLength: Int) {
        if (session != sid) {
            respond(client, 403, json.encodeToString(ErrorBody.serializer(), ErrorBody("rejected")))
            return
        }
        if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
            respond(client, 413, json.encodeToString(ErrorBody.serializer(), ErrorBody("too_large")))
            return
        }
        val buffer = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = reader.read(buffer, read, contentLength - read)
            if (n < 0) break
            read += n
        }
        val request = runCatching {
            json.decodeFromString(SyncRequest.serializer(), String(buffer, 0, read))
        }.getOrNull()
        if (request == null) {
            respond(client, 400, json.encodeToString(ErrorBody.serializer(), ErrorBody("invalid_body")))
            return
        }
        if (request.nodes.size > MAX_NODES) {
            respond(client, 413, json.encodeToString(ErrorBody.serializer(), ErrorBody("too_large")))
            return
        }
        val decision = runBlocking {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { confirm(IncomingRequest(request.from, request.nodes)) }
        }
        when (decision) {
            null -> respond(client, 408, json.encodeToString(ErrorBody.serializer(), ErrorBody("timeout")))
            is SyncDecision.Reject -> respond(client, 403, json.encodeToString(ErrorBody.serializer(), ErrorBody("rejected")))
            is SyncDecision.Accept -> respond(
                client, 200,
                json.encodeToString(SyncResponse.serializer(), SyncResponse(decision.nodes, decision.applied)),
            )
        }
    }

    private fun respond(client: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${reason(status)}\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        403 -> "Forbidden"
        404 -> "Not Found"
        408 -> "Request Timeout"
        413 -> "Payload Too Large"
        else -> "Error"
    }
}
```

- [ ] **步骤 4：运行测试**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pulse.tts.data.lansync.SyncServerTest"
```

预期：3 个测试 PASS。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/pulse/tts/data/lansync app/src/test/java/com/pulse/tts/data/lansync
git commit -m "feat(lan-sync): 按需 HTTP 服务端"
```

---

### 任务 3：出站客户端

**文件：**
- 创建：`app/src/main/java/com/pulse/tts/data/lansync/SyncClient.kt`

- [ ] **步骤 1：实现 `SyncClient.kt`**

```kotlin
package com.pulse.tts.data.lansync

import com.pulse.tts.data.AppJson
import com.pulse.tts.data.await
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class SyncOutcome {
    data class Ok(val response: SyncResponse) : SyncOutcome()
    object Rejected : SyncOutcome()
    object Timeout : SyncOutcome()
    data class Failed(val code: Int) : SyncOutcome()
}

/** 出站同步：短超时，不重试（同步是用户主动触发，重试只会放大等待）。 */
class SyncClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun ping(peer: SyncPeer): PingResponse? = runCatching {
        val request = Request.Builder()
            .url("http://${peer.host}:${peer.port}/v1/ping")
            .get()
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) return null
            AppJson.decodeFromString(PingResponse.serializer(), response.body?.string().orEmpty())
        }
    }.getOrNull()

    suspend fun sync(peer: SyncPeer, from: PeerInfo, nodes: List<SyncedNode>): SyncOutcome =
        runCatching {
            val body = AppJson.encodeToString(
                SyncRequest.serializer(),
                SyncRequest(from = from, nodes = nodes),
            ).toRequestBody(jsonType)
            val request = Request.Builder()
                .url("http://${peer.host}:${peer.port}/v1/sync")
                .header("X-Sync-Session", peer.sid)
                .post(body)
                .build()
            http.newCall(request).await().use { response ->
                when (response.code) {
                    200 -> SyncOutcome.Ok(
                        AppJson.decodeFromString(SyncResponse.serializer(), response.body?.string().orEmpty()),
                    )
                    403 -> SyncOutcome.Rejected
                    408 -> SyncOutcome.Timeout
                    else -> SyncOutcome.Failed(response.code)
                }
            }
        }.getOrElse { SyncOutcome.Failed(-1) }
}
```

- [ ] **步骤 2：编译**

```bash
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL（`kotlinx.serialization.builtins.serializer` 未使用，删除该 import）。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/pulse/tts/data/lansync/SyncClient.kt
git commit -m "feat(lan-sync): OkHttp 出站客户端"
```

---

### 任务 4：mDNS 发现

**文件：**
- 创建：`app/src/main/java/com/pulse/tts/data/lansync/MdnsDiscovery.kt`
- 修改：`app/src/main/java/com/pulse/tts/data/lansync/LanSyncEntry.kt`（本任务一并创建）

- [ ] **步骤 1：实现发现**

```kotlin
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

    fun start() {
        if (listener != null) return
        stop()
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
                if (sid != null && info.port > 0 && info.host != null) {
                    add(
                        SyncPeer(
                            host = info.host.hostAddress ?: return,
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
```

- [ ] **步骤 2：编译**

```bash
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/pulse/tts/data/lansync
git commit -m "feat(lan-sync): NsdManager 发现与深链入口"
```

---

### 任务 5：仓库合并方法

**文件：**
- 修改：`app/src/main/java/com/pulse/tts/data/NodeRepository.kt:66-132`

- [ ] **步骤 1：实现**

在 `NodeRepository` 的写操作区追加：

```kotlin
    /**
     * 局域网同步合并：只增不删，已有节点保留密钥、禁用与分组。
     * 单事务写入，不触发探测。
     */
    fun mergeSynced(remote: List<SyncedNode>): MergeStats {
        val current = _nodes.value
        val (merged, stats) = mergeSyncedNodes(current, remote)
        val changed = merged.filter { candidate -> current.none { it.id == candidate.id && it == candidate } }
        if (changed.isNotEmpty()) {
            store.insertOrUpdateAll(changed)
            _nodes.update { list -> changed.fold(list) { acc, node -> acc.withUpserted(node) } }
        }
        return stats
    }
```

补 import：

```kotlin
import com.pulse.tts.data.lansync.MergeStats
import com.pulse.tts.data.lansync.SyncedNode
import com.pulse.tts.data.lansync.mergeSyncedNodes
```

- [ ] **步骤 2：编译**

```bash
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/pulse/tts/data/NodeRepository.kt
git commit -m "feat(nodes): 支持局域网同步合并写入"
```

---

### 任务 6：ViewModel

**文件：**
- 创建：`app/src/main/java/com/pulse/tts/viewmodel/LanSyncViewModel.kt`
- 修改：`app/src/main/java/com/pulse/tts/App.kt:24`
- 修改：`app/src/main/AndroidManifest.xml:5`

- [ ] **步骤 1：暴露 appContext 并加权限**

`App.kt`：

```kotlin
class AppContainer(context: Context) {
    val appContext = context.applicationContext
```

其余引用 `appContext` 的位置不变（原是 `private val`）。

`AndroidManifest.xml` 增加：

```xml
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

- [ ] **步骤 2：实现 ViewModel**

```kotlin
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
    val nodes: List<com.pulse.tts.data.lansync.SyncedNode>,
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
            _state.value = LanSyncState(phase = LanSyncPhase.ERROR, message = "无法启动同步服务：${error.message}")
            return
        }
        server = instance
        discovery.start()
        _state.value = LanSyncState(
            phase = LanSyncPhase.RUNNING,
            port = port,
            sid = sid,
            localIp = localIpv4(),
            pairing = buildPairingString(SyncPeer(localIpv4(), port, sid, deviceName, "android")),
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
            when (val outcome = client.sync(peer, PeerInfo(deviceName, "android"), toSyncedNodes(repo.nodes.value))) {
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
```

- [ ] **步骤 3：编译**

```bash
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/pulse/tts/App.kt app/src/main/AndroidManifest.xml app/src/main/java/com/pulse/tts/viewmodel/LanSyncViewModel.kt
git commit -m "feat(lan-sync): 同步状态机与确认流程"
```

---

### 任务 7：同步页面

**文件：**
- 创建：`app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt`

- [ ] **步骤 1：实现页面**

```kotlin
package com.pulse.tts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pulse.tts.data.lansync.LanSyncEntry
import com.pulse.tts.ui.appViewModel
import com.pulse.tts.ui.components.PulseSubHeader
import com.pulse.tts.ui.components.SettingGroup
import com.pulse.tts.viewmodel.LanSyncPhase
import com.pulse.tts.viewmodel.LanSyncViewModel

@Composable
fun LanSyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: LanSyncViewModel = appViewModel {
        LanSyncViewModel(it.appContext, it.nodeRepository)
    }
    val state by vm.state.collectAsState()
    val peers by vm.peers.collectAsState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.start() }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    LaunchedEffect(Unit) {
        LanSyncEntry.pairing.collect { raw ->
            if (raw != null) {
                vm.consumeDeepLink()
                input = raw
                vm.syncFromString(raw)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PulseSubHeader(title = "局域网同步", subtitle = "与电脑双向合并节点", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "同一 Wi-Fi 下会自动发现电脑。也可以让电脑扫描或粘贴下方的配对串。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingGroup {
                Column(Modifier.padding(14.dp)) {
                    Text("本机配对串", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.pairing.ifBlank { "正在启动…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.localIp.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "地址 ${state.localIp}:${state.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("附近的电脑", style = MaterialTheme.typography.titleSmall)
            if (peers.isEmpty()) {
                Text(
                    "暂未发现设备。确认电脑已开启局域网同步，并与手机处于同一 Wi-Fi。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(peers, key = { it.sid }) { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.syncTo(peer) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(peer.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (peer.platform == "desktop") "电脑" else "手机",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("同步", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            if (peers.isEmpty()) Spacer(Modifier.weight(1f))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("粘贴配对串") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.syncFromString(input) },
                enabled = input.isNotBlank() && state.phase != LanSyncPhase.SYNCING,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Text(if (state.phase == LanSyncPhase.SYNCING) "同步中…" else "连接")
            }

            state.stats?.let { stats ->
                Text(
                    "同步完成：新增 ${stats.added}，更新 ${stats.updated}，跳过 ${stats.skipped}。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.reject() },
            icon = { androidx.compose.material3.Icon(Icons.Rounded.Sync, contentDescription = null) },
            title = { Text("${pending.fromName} 请求同步") },
            text = { Text("将发送 ${pending.nodes.size} 个节点，并接收本机节点。本机的密钥、禁用状态与分组不会被改动。") },
            confirmButton = { TextButton(onClick = { vm.accept() }) { Text("确认同步") } },
            dismissButton = { TextButton(onClick = { vm.reject() }) { Text("拒绝") } },
        )
    }
}
```

- [ ] **步骤 2：编译**

```bash
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。若 `Modifier.weight` 在 `Column` 中报错，确认使用 `Modifier.weight(1f)` 时已导入 `androidx.compose.foundation.layout.ColumnScope` 内的扩展（无需额外 import）。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt
git commit -m "feat(lan-sync): 同步页面"
```

---

### 任务 8：入口、导航与深链

**文件：**
- 修改：`app/src/main/java/com/pulse/tts/ui/screens/SettingsScreen.kt:87-94,303-328`
- 修改：`app/src/main/java/com/pulse/tts/navigation/NavViewModel.kt:11-21,71-78`
- 修改：`app/src/main/java/com/pulse/tts/navigation/AppNav.kt:26-28,87-94`
- 修改：`app/src/main/AndroidManifest.xml:19-28`
- 修改：`app/src/main/java/com/pulse/tts/MainActivity.kt:36-41,67-77`

- [ ] **步骤 1：导航**

`NavViewModel.kt`：

```kotlin
    object LanSync : Screen()
```

```kotlin
    fun goLanSync() = push(Screen.LanSync)
```

`tabOf()` 的 `else` 分支已覆盖新页面，无需改动。

- [ ] **步骤 2：设置页入口**

`SettingsScreen` 增加参数：

```kotlin
    onOpenLanSync: () -> Unit,
```

在「数据」分组内、「导出配置」之前插入：

```kotlin
                SettingDivider()
                SettingRow(
                    title = "局域网同步",
                    subtitle = "同一 Wi-Fi 下与电脑双向合并节点",
                    icon = Icons.Rounded.SwapHoriz,
                    onClick = onOpenLanSync,
                )
```

（`SwapHoriz` 已在文件 import 列表中。）

- [ ] **步骤 3：AppNav 接线**

```kotlin
import com.pulse.tts.data.lansync.LanSyncEntry
import com.pulse.tts.ui.screens.LanSyncScreen
```

```kotlin
            is Screen.Settings -> SettingsScreen(
                onBack = { nav.handleBack() },
                onOpenPermissions = nav::goPermissions,
                onOpenAdmin = nav::goAdmin,
                onOpenAbout = nav::goAbout,
                onOpenVoice = { voicePickerVisible = true },
                onOpenLanSync = nav::goLanSync,
                onToast = onToast,
            )
            is Screen.LanSync -> LanSyncScreen(onBack = { nav.handleBack() })
```

在 `LaunchedEffect(playback.isActive)` 之后增加：

```kotlin
    LaunchedEffect(Unit) {
        LanSyncEntry.pairing.collect { raw ->
            if (raw != null) nav.goLanSync()
        }
    }
```

- [ ] **步骤 4：Manifest 深链**

在 `MainActivity` 的 `intent-filter` 之后追加：

```xml
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="pulsetts" android:host="sync" />
            </intent-filter>
```

- [ ] **步骤 5：MainActivity 接收**

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        // ...原有代码
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (data.startsWith("pulsetts://sync", ignoreCase = true)) {
            com.pulse.tts.data.lansync.LanSyncEntry.offer(data)
        }
    }
```

补 import：

```kotlin
import android.content.Intent
```

- [ ] **步骤 6：编译**

```bash
./gradlew :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 7：Commit**

```bash
git add app/src/main
git commit -m "feat(lan-sync): 设置入口、导航与 pulsetts 深链"
```

---

### 任务 9：全量验证与真机验收

- [ ] **步骤 1：单元测试与构建**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

预期：全部测试 PASS，APK 生成成功。

- [ ] **步骤 2：安装到手机**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **步骤 3：手工核对**

1. 设置 → 数据 → 局域网同步，进入页面后自动显示配对串与本机地址。
2. 电脑开启同步 → 手机列表出现电脑（设备名 + 平台）→ 点击同步 → 电脑弹确认。
3. 电脑确认后，手机显示「新增 / 更新 / 跳过」，节点列表出现电脑节点。
4. 电脑拒绝 → 手机提示「对方拒绝了本次同步」。
5. 电脑扫码或粘贴手机配对串 → 手机弹确认 → 确认后双向合并。
6. 重复同步不产生重复节点，本地密钥、禁用状态、分组保持不变。
7. 退出页面后，服务停止（用 `adb shell netstat` 或电脑侧连接确认端口不可达）。
8. 关闭 mDNS（隔离 AP 或关闭组播）→ 扫码 / 粘贴配对串仍可同步。
9. 60 秒不操作确认框 → 发起方收到超时提示。

- [ ] **步骤 4：Commit（如有调整）**

```bash
git add -A
git commit -m "chore(lan-sync): 真机验收修正"
```

---

## 规格覆盖对照

| 规格条目 | 对应任务 |
| --- | --- |
| `_pulse-tts._tcp` 发现与 TXT 解析 | 任务 4 |
| 按需 HTTP 服务端、随机端口 | 任务 2 |
| `GET /v1/ping` / `POST /v1/sync` | 任务 2、3 |
| sid 校验、403/408/413、60 秒确认 | 任务 2 |
| 出站 OkHttp、8 秒/短超时 | 任务 3 |
| 合并只增不删、保留密钥/禁用/分组 | 任务 1、5 |
| 同步后不自动探测 | 任务 5（只写库，不调 probeAll） |
| 设置页入口与独立页面 | 任务 7、8 |
| `pulsetts://` 深链（系统相机扫码） | 任务 4、8 |
| `CHANGE_WIFI_MULTICAST_STATE` + MulticastLock | 任务 6 |
| 错误提示 | 任务 6、7 |
| 单元 / 集成 / 真机测试 | 任务 1、2、9 |
