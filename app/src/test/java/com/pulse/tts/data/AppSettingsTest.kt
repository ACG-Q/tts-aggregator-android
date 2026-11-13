package com.pulse.tts.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `外观按系统浅色深色循环`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.next())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.next())
    }

    @Test
    fun `跟随系统时采用系统深浅色`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
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
    fun `局域网自动允许可序列化往返`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val encoded = json.encodeToString(AppSettings.serializer(), AppSettings(lanSyncAutoAllow = true))
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertTrue(decoded.lanSyncAutoAllow)
    }
}
