package com.pulse.tts.data

import kotlinx.serialization.Serializable

/**
 * Release 资产 `update.json` 的模型。
 *
 * 所有字段都有默认值，服务端增删字段不会导致解析失败；
 * 是否有效由 [parseUpdateInfo] 统一判定。
 */
@Serializable
data class UpdateInfo(
    val versionName: String = "",
    val versionCode: Int = 0,
    val minSupportedVersionCode: Int = 0,
    val releasePage: String = "",
    val notes: String = "",
    val apks: Map<String, String> = emptyMap(),
    val sha256: Map<String, String> = emptyMap(),
)

/** 检查更新的判定结果 */
sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    data class Optional(val info: UpdateInfo) : UpdateDecision
    data class Required(val info: UpdateInfo) : UpdateDecision
}

/** 按 ABI 选中的下载目标 */
data class ApkTarget(val abi: String, val url: String)

/** 启动静默检查的最小间隔 */
const val UPDATE_CHECK_INTERVAL_MS: Long = 6 * 60 * 60 * 1000L

/**
 * 攻击面：JSON 由 GitHub Release 提供，字段缺失、类型错误或 versionCode 非法都返回 null。
 */
fun parseUpdateInfo(raw: String): UpdateInfo? =
    runCatching { AppJson.decodeFromString<UpdateInfo>(raw) }
        .getOrNull()
        ?.takeIf { it.versionCode > 0 }

/** 版本判定：始终比较 versionCode，不看版本号字符串。 */
fun decideUpdate(currentVersionCode: Int, info: UpdateInfo?): UpdateDecision {
    if (info == null || info.versionCode <= 0) return UpdateDecision.UpToDate
    val minSupported = info.minSupportedVersionCode.coerceAtLeast(0)
    return when {
        currentVersionCode < minSupported -> UpdateDecision.Required(info)
        currentVersionCode < info.versionCode -> UpdateDecision.Optional(info)
        else -> UpdateDecision.UpToDate
    }
}

/** 启动检查节流：首次、超过间隔或系统时间回拨时允许检查。 */
fun shouldCheckOnLaunch(lastCheckAt: Long, now: Long): Boolean =
    lastCheckAt <= 0L ||
        now < lastCheckAt ||
        now - lastCheckAt >= UPDATE_CHECK_INTERVAL_MS

/** 加速地址是否可接受：留空表示直连，否则只接受 http(s)。 */
fun isValidGithubProxy(raw: String): Boolean {
    val text = raw.trim()
    if (text.isEmpty()) return true
    val scheme = when {
        text.startsWith("https://", ignoreCase = true) -> "https://"
        text.startsWith("http://", ignoreCase = true) -> "http://"
        else -> return false
    }
    val rest = text.substring(scheme.length)
    return rest.isNotBlank() && rest.none { it.isWhitespace() }
}

/**
 * 规范化加速地址：留空返回空串，非法返回 null，合法时补尾部斜杠。
 */
fun normalizeGithubProxy(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return ""
    if (!isValidGithubProxy(text)) return null
    return if (text.endsWith("/")) text else "$text/"
}

/**
 * 代理前缀拼接：`https://ghproxy.net/` + `https://github.com/...`
 * 留空代理原样返回，供直连场景复用。
 */
fun buildProxiedUrl(proxy: String, url: String): String {
    val prefix = proxy.trim().removeTrailingSlash()
    return if (prefix.isEmpty()) url else "$prefix/$url"
}

/** 按设备 ABI 优先选择安装包，最后回退 universal 与任意可用项。 */
fun pickApk(info: UpdateInfo, abis: List<String>): ApkTarget? {
    for (abi in abis) {
        val url = info.apks[abi]?.trim().orEmpty()
        if (url.isNotEmpty()) return ApkTarget(abi, url)
    }
    val universal = info.apks["universal"]?.trim().orEmpty()
    if (universal.isNotEmpty()) return ApkTarget("universal", universal)
    return info.apks.entries
        .firstOrNull { it.value.isNotBlank() }
        ?.let { ApkTarget(it.key, it.value.trim()) }
}
