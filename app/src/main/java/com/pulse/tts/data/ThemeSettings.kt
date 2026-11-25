package com.pulse.tts.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 主题配置的解码兼容层。
 *
 * 1.1 之前用一个 `theme` 字段混合表达明暗与风格。现在拆成 `themeStyle` +
 * `themeSkin`：新配置直接读取新字段；旧配置按 [legacyThemeSelection] 迁移，
 * 没有任何主题字段时使用 [ThemeDefaults]。
 */

internal data class ThemeSelection(
    val style: ThemeStyle,
    val skin: ThemeSkin,
)

/** 旧版 `ThemeMode` 名称 → 新双字段；未知值返回 null。 */
internal fun legacyThemeSelection(legacy: String?): ThemeSelection? = when (legacy) {
    "SYSTEM" -> ThemeSelection(ThemeStyle.SYSTEM, ThemeSkin.NORMAL)
    "LIGHT" -> ThemeSelection(ThemeStyle.LIGHT, ThemeSkin.NORMAL)
    "DARK" -> ThemeSelection(ThemeStyle.DARK, ThemeSkin.NORMAL)
    "INK" -> ThemeSelection(ThemeStyle.SYSTEM, ThemeSkin.INK)
    "GLASS" -> ThemeSelection(ThemeStyle.SYSTEM, ThemeSkin.GLASS)
    else -> null
}

/** 从 JSON 对象解码应用设置；旧格式会补齐主题字段。 */
internal fun decodeAppSettings(json: Json, obj: JsonObject): AppSettings? {
    val settings = runCatching {
        json.decodeFromJsonElement(AppSettings.serializer(), obj)
    }.getOrNull() ?: return null
    if ("themeStyle" in obj || "themeSkin" in obj) return settings

    val legacy = runCatching { obj["theme"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    val selection = legacyThemeSelection(legacy) ?: return settings
    return settings.copy(themeStyle = selection.style, themeSkin = selection.skin)
}

/** 从原始 JSON 文本解码应用设置，兼容旧 `theme` 字段。 */
internal fun decodeAppSettings(json: Json, raw: String): AppSettings? {
    val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    return decodeAppSettings(json, obj)
}
