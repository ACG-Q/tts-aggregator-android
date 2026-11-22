package com.pulse.tts.ui.theme

import com.pulse.tts.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolutionTest {

    @Test
    fun `system follows device darkness`() {
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, true, ThemePalette.DARK),
            resolveTheme(ThemeMode.SYSTEM, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, false, ThemePalette.LIGHT),
            resolveTheme(ThemeMode.SYSTEM, systemDark = false),
        )
    }

    @Test
    fun `light and dark ignore device`() {
        assertFalse(resolveTheme(ThemeMode.LIGHT, systemDark = true).dark)
        assertEquals(ThemePalette.LIGHT, resolveTheme(ThemeMode.LIGHT, systemDark = true).palette)
        assertTrue(resolveTheme(ThemeMode.DARK, systemDark = false).dark)
        assertEquals(ThemePalette.DARK, resolveTheme(ThemeMode.DARK, systemDark = false).palette)
    }

    @Test
    fun `ink keeps style and follows device`() {
        assertEquals(
            ResolvedTheme(AppStyle.INK, true, ThemePalette.INK_DARK),
            resolveTheme(ThemeMode.INK, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.INK, false, ThemePalette.INK_LIGHT),
            resolveTheme(ThemeMode.INK, systemDark = false),
        )
    }

    @Test
    fun `glass keeps style and follows device`() {
        assertEquals(
            ResolvedTheme(AppStyle.GLASS, true, ThemePalette.GLASS_DARK),
            resolveTheme(ThemeMode.GLASS, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.GLASS, false, ThemePalette.GLASS_LIGHT),
            resolveTheme(ThemeMode.GLASS, systemDark = false),
        )
    }
}
