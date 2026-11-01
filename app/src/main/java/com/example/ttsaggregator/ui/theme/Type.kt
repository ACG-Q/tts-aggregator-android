package com.example.ttsaggregator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 手机字阶：标题更疏、正文更松，朗读优先界面允许一屏只做一件事。
 * display/headline 只用于品牌区；列表与控件使用 titleSmall 以下。
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
)

/** 日志 / URL / 数值用等宽，小一号 */
val MonoTextStyle = TextStyle(
    fontSize = 11.sp, lineHeight = 15.sp,
    fontFamily = FontFamily.Monospace,
)
