package com.pulse.tts.data

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val backgroundPlay: Boolean = true,
    val resume: Boolean = true,
    val autoTestOnLaunch: Boolean = true,
    val cooldownMinutes: Int = 10,
    val failThreshold: Int = 3,
    val sleepMinutes: Int = 0,
    val lanSyncAutoAllow: Boolean = false,
    /** GitHub 加速前缀，留空表示直连；只用于检查更新与 APK 下载 */
    val githubProxy: String = "",
)

object AppSettingsOptions {
    val COOLDOWN_MINUTES = listOf(1, 5, 10, 30, 60)
    val FAIL_THRESHOLDS = listOf(2, 3, 5, 10)
    val SLEEP_MINUTES = listOf(0, 15, 30, 60)
}
