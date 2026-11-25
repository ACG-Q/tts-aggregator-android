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
    ) = resolveTheme(style, skin, systemDark)

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
    fun `glass combines with every style`() {
        assertEquals(
            ResolvedTheme(AppStyle.GLASS, false, ThemePalette.GLASS_LIGHT),
            resolve(ThemeStyle.SYSTEM, ThemeSkin.GLASS, systemDark = false),
        )
        assertEquals(
            ResolvedTheme(AppStyle.GLASS, true, ThemePalette.GLASS_DARK),
            resolve(ThemeStyle.SYSTEM, ThemeSkin.GLASS, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.GLASS, false, ThemePalette.GLASS_LIGHT),
            resolve(ThemeStyle.LIGHT, ThemeSkin.GLASS, systemDark = true),
        )
        assertEquals(
            ResolvedTheme(AppStyle.GLASS, true, ThemePalette.GLASS_DARK),
            resolve(ThemeStyle.DARK, ThemeSkin.GLASS, systemDark = false),
        )
    }
}
