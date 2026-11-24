package com.pulse.tts.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GitHub Releases 列表接口的最小模型，用于发现预发布版本。 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val draft: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

/** 发布流程固定产出的更新清单资产名。 */
const val UPDATE_MANIFEST_ASSET = "update.json"

/** 全部版本渠道一次最多探测多少个 release，避免一次检查发出过多请求。 */
const val UPDATE_MANIFEST_CANDIDATES = 5

/** 解析 Release 列表；JSON 损坏或字段类型不符时返回空列表。 */
fun parseReleaseList(raw: String): List<GithubRelease> =
    runCatching { AppJson.decodeFromString<List<GithubRelease>>(raw) }.getOrElse { emptyList() }

/**
 * 从 Release 列表里挑出可用的清单地址。
 *
 * 跳过 draft、缺少 update.json 与空白地址的条目，保持接口返回顺序，最多取 [limit] 个。
 */
fun selectManifestCandidates(
    releases: List<GithubRelease>,
    limit: Int = UPDATE_MANIFEST_CANDIDATES,
): List<String> {
    if (limit <= 0) return emptyList()
    return releases.asSequence()
        .filterNot { it.draft }
        .mapNotNull { release ->
            release.assets
                .firstOrNull { it.name == UPDATE_MANIFEST_ASSET }
                ?.browserDownloadUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        .take(limit)
        .toList()
}

/** 在多个清单里挑 versionCode 最大的一个；忽略非法版本号。 */
fun pickNewestInfo(infos: List<UpdateInfo>): UpdateInfo? =
    infos.filter { it.versionCode > 0 }.maxByOrNull { it.versionCode }

/**
 * 启动检查的最终决策：已最新或被静默时返回 null，其余交给调用方弹窗。
 *
 * 强制更新不受关闭记忆影响，始终返回 [UpdateDecision.Required]。
 */
fun decideLaunchUpdate(
    currentVersionCode: Int,
    info: UpdateInfo?,
    dismissedCode: Int,
    dismissedAt: Long,
    now: Long,
): UpdateDecision? = when (val decision = decideUpdate(currentVersionCode, info)) {
    UpdateDecision.UpToDate -> null
    is UpdateDecision.Required -> decision
    is UpdateDecision.Optional ->
        if (shouldSilenceDismissal(dismissedCode, dismissedAt, decision.info.versionCode, now)) {
            null
        } else {
            decision
        }
}

/**
 * 「稍后」静默：同一版本在检查间隔内不再自动提醒。
 *
 * 时间回拨（now < dismissedAt）也视为静默，避免反复打扰。
 */
fun shouldSilenceDismissal(
    dismissedCode: Int,
    dismissedAt: Long,
    versionCode: Int,
    now: Long,
): Boolean {
    if (dismissedCode <= 0 || dismissedAt <= 0L) return false
    if (dismissedCode != versionCode) return false
    if (now < dismissedAt) return true
    return now - dismissedAt < UPDATE_CHECK_INTERVAL_MS
}
