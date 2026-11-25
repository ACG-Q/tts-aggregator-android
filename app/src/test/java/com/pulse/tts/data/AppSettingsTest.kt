package com.pulse.tts.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `跟随系统时采用系统深浅色`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun `新主题沿用系统深浅`() {
        assertTrue(ThemeMode.INK.isDark(systemDark = true))
        assertFalse(ThemeMode.INK.isDark(systemDark = false))
        assertTrue(ThemeMode.GLASS.isDark(systemDark = true))
        assertFalse(ThemeMode.GLASS.isDark(systemDark = false))
    }

    @Test
    fun `主题枚举可序列化往返`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        ThemeMode.entries.forEach { mode ->
            val encoded = json.encodeToString(ThemeMode.serializer(), mode)
            assertEquals(mode, json.decodeFromString(ThemeMode.serializer(), encoded))
        }
    }

    @Test
    fun `默认设置符合原型`() {
        val s = AppSettings()
        assertEquals(ThemeMode.SYSTEM, s.theme)
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
    fun `局域网自动允许可序列化往返`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val encoded = json.encodeToString(AppSettings.serializer(), AppSettings(lanSyncAutoAllow = true))
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertTrue(decoded.lanSyncAutoAllow)
    }
}
