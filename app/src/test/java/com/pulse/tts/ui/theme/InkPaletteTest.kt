package com.pulse.tts.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class InkPaletteTest {

    @Test
    fun `ink palettes contain only grayscale opaque colors`() {
        listOf(InkLightColors, InkDarkColors).forEach { scheme ->
            scheme.allColors().forEach { color ->
                assertEquals("${color.red} != ${color.green}", color.red, color.green, 0.0001f)
                assertEquals("${color.green} != ${color.blue}", color.green, color.blue, 0.0001f)
                assertEquals("alpha must be opaque", 1f, color.alpha, 0.0001f)
            }
        }
    }
}

private fun ColorScheme.allColors(): List<Color> = listOf(
    primary, onPrimary, primaryContainer, onPrimaryContainer,
    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
    tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
    error, onError, errorContainer, onErrorContainer,
    background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
    outline, outlineVariant,
    surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
    surfaceContainerHigh, surfaceContainerHighest,
)
