package com.example.ttsaggregator.ui.theme

import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScaleTest {

    private val styles = mapOf(
        "displaySmall" to AppTypography.displaySmall,
        "headlineMedium" to AppTypography.headlineMedium,
        "headlineSmall" to AppTypography.headlineSmall,
        "titleLarge" to AppTypography.titleLarge,
        "titleMedium" to AppTypography.titleMedium,
        "titleSmall" to AppTypography.titleSmall,
        "bodyLarge" to AppTypography.bodyLarge,
        "bodyMedium" to AppTypography.bodyMedium,
        "bodySmall" to AppTypography.bodySmall,
        "labelLarge" to AppTypography.labelLarge,
        "labelMedium" to AppTypography.labelMedium,
        "labelSmall" to AppTypography.labelSmall,
    )

    @Test
    fun everyStyleDisablesFontSynthesis() {
        styles.forEach { (name, style) ->
            assertEquals("$name 必须关闭字体合成", FontSynthesis.None, style.fontSynthesis)
        }
    }

    @Test
    fun everyStyleUsesNormalWeight() {
        styles.forEach { (name, style) ->
            assertEquals("$name 不应再用字重强调", FontWeight.Normal, style.fontWeight)
        }
    }

    @Test
    fun scaleIsNonDecreasing() {
        val ordered = listOf(
            "labelSmall", "labelMedium", "bodySmall", "labelLarge", "bodyMedium",
            "bodyLarge", "titleSmall", "titleMedium", "titleLarge",
            "headlineSmall", "headlineMedium", "displaySmall",
        )
        val sizes = ordered.map { styles.getValue(it).fontSize.value }
        assertEquals(sizes.sorted(), sizes)
    }

    @Test
    fun lineHeightKeepsReadableRatio() {
        styles.forEach { (name, style) ->
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue("$name 行高倍率 $ratio 应不小于 1.2", ratio >= 1.2f)
        }
    }
}
