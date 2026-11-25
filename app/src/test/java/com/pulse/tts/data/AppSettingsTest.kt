package com.pulse.tts.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `样式决定系统深浅`() {
        assertTrue(ThemeStyle.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeStyle.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeStyle.DARK.isDark(systemDark = false))
        assertFalse(ThemeStyle.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun `样式与主题枚举可序列化往返`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        ThemeStyle.entries.forEach { style ->
            val encoded = json.encodeToString(ThemeStyle.serializer(), style)
            assertEquals(style, json.decodeFromString(ThemeStyle.serializer(), encoded))
        }
        ThemeSkin.entries.forEach { skin ->
            val encoded = json.encodeToString(ThemeSkin.serializer(), skin)
            assertEquals(skin, json.decodeFromString(ThemeSkin.serializer(), encoded))
        }
    }

    @Test
    fun `墨水屏默认包选择墨水屏主题`() {
        assertEquals(ThemeSkin.INK, ThemeDefaults.skinFor(inkDefault = true))
        assertEquals(ThemeSkin.NORMAL, ThemeDefaults.skinFor(inkDefault = false))
    }

    @Test
    fun `默认设置符合原型`() {
        val s = AppSettings()
        assertEquals(ThemeStyle.SYSTEM, s.themeStyle)
        assertTrue(s.haptics)
        assertTrue(s.backgroundPlay)
        assertTrue(s.resume)
        assertTrue(s.autoTestOnLaunch)
        assertEquals(10, s.cooldownMinutes)
        assertEquals(3, s.failThreshold)
        assertEquals(0, s.sleepMinutes)
    }

    @Test
    fun `局域网自动允许默认关闭`() {
        assertFalse(AppSettings().lanSyncAutoAllow)
    }

    @Test
    fun `语音缓存默认保留 7 天`() {
        assertEquals(7, AppSettings().audioCacheDays)
    }

    @Test
    fun `主题默认跟随系统与构建默认主题`() {
        val settings = AppSettings()
        assertEquals(ThemeDefaults.style, settings.themeStyle)
        assertEquals(ThemeDefaults.skin, settings.themeSkin)
    }

    @Test
    fun `局域网自动允许可序列化往返`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val encoded = json.encodeToString(AppSettings.serializer(), AppSettings(lanSyncAutoAllow = true))
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertTrue(decoded.lanSyncAutoAllow)
    }
}
