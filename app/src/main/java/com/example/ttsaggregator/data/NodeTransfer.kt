package com.example.ttsaggregator.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class NodeDraft(
    val url: String,
    val label: String = "",
    val apiKey: String? = null,
    val group: String = "默认",
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

fun parseNodeImport(raw: String): List<NodeDraft> {
    if (raw.trimStart().startsWith("{")) {
        val config = runCatching {
            transferJson.decodeFromString(ExportedConfig.serializer(), raw)
        }.getOrNull() ?: return emptyList()
        return config.nodes.mapNotNull { node ->
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
    }
    return raw.lines().mapNotNull { line ->
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
