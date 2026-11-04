package com.pulse.tts.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class NodeDraft(
    val url: String,
    val label: String = "",
    val apiKey: String? = null,
    val group: String = "默认",
)

/**
 * 一次导入的完整结果。
 *
 * [settings] 只在导入 JSON 配置且解析成功时非空；纯文本清单导入时恒为 null，
 * 避免"导入节点"顺手把用户当前偏好改掉。
 */
data class NodeImport(
    val drafts: List<NodeDraft>,
    val settings: AppSettings? = null,
)

@Serializable
private data class ExportedNode(
    val name: String,
    val url: String,
    val group: String,
    val health: String,
    val keyless: Boolean,
)

@Serializable
private data class ExportedConfig(
    val version: Int = 1,
    val exportedAt: String,
    val count: Int,
    val settings: AppSettings,
    val nodes: List<ExportedNode>,
)

private val transferJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val NODE_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

/**
 * 解析导入内容，同时返回可恢复的偏好设置。
 *
 * JSON 配置含 `settings` 字段，用于「恢复配置」；纯文本清单只解析节点。
 * 密钥不在导出文件中，因此两类导入都不会带密钥。
 */
fun parseNodeBundle(raw: String): NodeImport {
    if (raw.trimStart().startsWith("{")) {
        val config = runCatching {
            transferJson.decodeFromString(ExportedConfig.serializer(), raw)
        }.getOrNull() ?: return NodeImport(emptyList())
        val drafts = config.nodes.mapNotNull { node ->
            val url = node.url.trim().removeSuffix("/")
            if (!NODE_URL.matches(url)) {
                null
            } else {
                NodeDraft(
                    url = url,
                    label = node.name,
                    apiKey = null,
                    group = node.group.ifBlank { "默认" },
                )
            }
        }
        return NodeImport(drafts = drafts, settings = config.settings)
    }
    return NodeImport(drafts = parseTextNodes(raw))
}

/** 只取节点，丢弃偏好设置（节点管理里的批量添加走这条路径）。 */
fun parseNodeImport(raw: String): List<NodeDraft> = parseNodeBundle(raw).drafts

private fun parseTextNodes(raw: String): List<NodeDraft> = raw.lines().mapNotNull { line ->
    val parts = line.trim().split("|")
    val url = parts.getOrNull(0)?.trim()?.removeSuffix("/").orEmpty()
    if (!NODE_URL.matches(url)) {
        null
    } else {
        NodeDraft(
            url = url,
            label = parts.getOrNull(1)?.trim().orEmpty(),
            apiKey = null,
            group = parts.getOrNull(2)?.trim()?.ifBlank { "默认" } ?: "默认",
        )
    }
}

fun exportNodesJson(
    nodes: List<TtsNode>,
    config: AppSettings,
    exportedAt: String,
): String = transferJson.encodeToString(
    ExportedConfig.serializer(),
    ExportedConfig(
        exportedAt = exportedAt,
        count = nodes.size,
        settings = config,
        nodes = nodes.map {
            ExportedNode(
                name = it.label.ifBlank { it.baseUrl },
                url = it.baseUrl,
                group = it.group,
                health = it.health.name,
                keyless = it.keyless,
            )
        },
    ),
)
