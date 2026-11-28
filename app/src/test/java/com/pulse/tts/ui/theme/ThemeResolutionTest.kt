package com.pulse.tts.ui.theme

import com.pulse.tts.data.ThemeSkin
import com.pulse.tts.data.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolutionTest {

    private fun resolve(
        style: ThemeStyle,
        skin: ThemeSkin = ThemeSkin.NORMAL,
        systemDark: Boolean = false,
        dynamicAvailable: Boolean = true,
    ) = resolveTheme(style, skin, systemDark, dynamicAvailable)

    @Test
    fun `system follows device darkness`() {
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, true, ThemePalette.DARK),
            resolve(ThemeStyle.SYSTEM, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, false, ThemePalette.LIGHT),
            resolve(ThemeStyle.SYSTEM, systemDark = false),
        )
    }

    @Test
    fun `light and dark ignore device`() {
        assertFalse(resolve(ThemeStyle.LIGHT, systemDark = true).dark)
        assertEquals(ThemePalette.LIGHT, resolve(ThemeStyle.LIGHT, systemDark = true).palette)
        assertTrue(resolve(ThemeStyle.DARK, systemDark = false).dark)
        assertEquals(ThemePalette.DARK, resolve(ThemeStyle.DARK, systemDark = false).palette)
    }

    @Test
    fun `ink combines with every style`() {
        assertEquals(
            ResolvedTheme(AppStyle.INK, false, ThemePalette.INK_LIGHT),
            resolve(ThemeStyle.SYSTEM, ThemeSkin.INK, systemDark = false),
        )
        assertEquals(
            ResolvedTheme(AppStyle.INK, true, ThemePalette.INK_DARK),
            resolve(ThemeStyle.SYSTEM, ThemeSkin.INK, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.INK, false, ThemePalette.INK_LIGHT),
            resolve(ThemeStyle.LIGHT, ThemeSkin.INK, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.INK, true, ThemePalette.INK_DARK),
            resolve(ThemeStyle.DARK, ThemeSkin.INK, systemDark = false),
        )
    }

    @Test
    fun `material you uses dynamic palette when available`() {
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, false, ThemePalette.DYNAMIC_LIGHT),
            resolve(ThemeStyle.LIGHT, ThemeSkin.MATERIAL_YOU, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, true, ThemePalette.DYNAMIC_DARK),
            resolve(ThemeStyle.DARK, ThemeSkin.MATERIAL_YOU, systemDark = false),
        )
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, true, ThemePalette.DYNAMIC_DARK),
            resolve(ThemeStyle.SYSTEM, ThemeSkin.MATERIAL_YOU, systemDark = true),
        )
    }

    @Test
    fun `material you falls back to normal palette without dynamic color`() {
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, false, ThemePalette.LIGHT),
            resolve(ThemeStyle.LIGHT, ThemeSkin.MATERIAL_YOU, dynamicAvailable = false),
        )
        assertEquals(
            ResolvedTheme(AppStyle.STANDARD, true, ThemePalette.DARK),
            resolve(ThemeStyle.DARK, ThemeSkin.MATERIAL_YOU, dynamicAvailable = false),
        )
    }

    @Test
    fun `dynamic color requires android 12`() {
        assertFalse(dynamicColorAvailable(30))
        assertTrue(dynamicColorAvailable(31))
    }

    @Test
    fun `material you is hidden without dynamic color`() {
        assertEquals(
            listOf(ThemeSkin.NORMAL, ThemeSkin.MATERIAL_YOU, ThemeSkin.INK),
            visibleSkins(dynamicAvailable = true),
        )
        assertEquals(
            listOf(ThemeSkin.NORMAL, ThemeSkin.INK),
            visibleSkins(dynamicAvailable = false),
        )
    }
}
