package com.pulse.tts.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeSettingsTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `旧模式映射到样式与主题`() {
        assertEquals(
            ThemeSelection(ThemeStyle.SYSTEM, ThemeSkin.NORMAL),
            legacyThemeSelection("SYSTEM"),
        )
        assertEquals(
            ThemeSelection(ThemeStyle.LIGHT, ThemeSkin.NORMAL),
            legacyThemeSelection("LIGHT"),
        )
        assertEquals(
            ThemeSelection(ThemeStyle.DARK, ThemeSkin.NORMAL),
            legacyThemeSelection("DARK"),
        )
        assertEquals(
            ThemeSelection(ThemeStyle.SYSTEM, ThemeSkin.INK),
            legacyThemeSelection("INK"),
        )
        assertEquals(
            ThemeSelection(ThemeStyle.SYSTEM, ThemeSkin.GLASS),
            legacyThemeSelection("GLASS"),
        )
        assertNull(legacyThemeSelection(null))
        assertNull(legacyThemeSelection("UNKNOWN"))
    }

    @Test
    fun `旧 JSON 的主题字段迁移到新字段`() {
        val decoded = decodeAppSettings(
            json,
            """{"theme":"INK","cooldownMinutes":30}""",
        )
        assertEquals(ThemeStyle.SYSTEM, decoded?.themeStyle)
        assertEquals(ThemeSkin.INK, decoded?.themeSkin)
        assertEquals(30, decoded?.cooldownMinutes)
    }

    @Test
    fun `旧浅色配置迁移后保持浅色`() {
        val decoded = decodeAppSettings(json, """{"theme":"LIGHT"}""")
        assertEquals(ThemeStyle.LIGHT, decoded?.themeStyle)
        assertEquals(ThemeSkin.NORMAL, decoded?.themeSkin)
    }

    @Test
    fun `新 JSON 直接读取双字段`() {
        val decoded = decodeAppSettings(
            json,
            """{"themeStyle":"DARK","themeSkin":"GLASS"}""",
        )
        assertEquals(ThemeStyle.DARK, decoded?.themeStyle)
        assertEquals(ThemeSkin.GLASS, decoded?.themeSkin)
    }

    @Test
    fun `没有任何主题字段时使用构建默认值`() {
        val decoded = decodeAppSettings(json, """{"cooldownMinutes":10}""")
        assertEquals(ThemeDefaults.style, decoded?.themeStyle)
        assertEquals(ThemeDefaults.skin, decoded?.themeSkin)
    }
}
