package com.example.ttsaggregator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 紧凑字阶（面向手机）：整体比 Material 默认小 1-2 号，
 * 行高同步收紧，保证一屏信息密度。
 *
 * display/headline 只在「关于」页用；日常界面最高用到 titleLarge(17)。
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
)

/** 日志 / URL / 数值用等宽，小一号 */
val MonoTextStyle = TextStyle(
    fontSize = 11.sp, lineHeight = 15.sp,
    fontFamily = FontFamily.Monospace,
)
