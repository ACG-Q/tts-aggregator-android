package com.pulse.tts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 中文只有 Regular 字重，Bold / SemiBold 会被系统描边合成，笔画糊成一团。
 * 因此全站只用 Normal 字重，用字号、颜色与留白建立层级，并关闭字体合成。
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 28.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    headlineMedium = TextStyle(
        fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 23.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.5.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    bodySmall = TextStyle(
        fontSize = 12.5.sp, lineHeight = 19.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp, lineHeight = 19.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 17.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
    ),
)

/** 日志 / URL / 数值用等宽，小一号 */
val MonoTextStyle = TextStyle(
    fontSize = 11.sp, lineHeight = 16.sp,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None,
)
