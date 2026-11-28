package com.pulse.tts.ui.theme

import com.pulse.tts.data.ThemeSkin
import com.pulse.tts.data.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeLabelsTest {

    @Test
    fun `material you has its own label`() {
        assertEquals("Material You", skinLabel(ThemeSkin.MATERIAL_YOU))
    }

    @Test
    fun `material you summary follows the style rule`() {
        assertEquals(
            "Material You",
            themeSummary(ThemeStyle.SYSTEM, ThemeSkin.MATERIAL_YOU),
        )
        assertEquals(
            "Material You · 深色",
            themeSummary(ThemeStyle.DARK, ThemeSkin.MATERIAL_YOU),
        )
    }
}
