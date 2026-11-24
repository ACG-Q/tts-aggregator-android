package com.pulse.tts.data

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    INK,
    GLASS;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM, INK, GLASS -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** 更新渠道：正式版只收正式发布，全部版本包含预发布。 */
@Serializable
enum class UpdateChannel {
    STABLE,
    ALL,
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
    /** 更新渠道，默认只收正式发布 */
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    /** 最近一次被「稍后」关掉的更新版本，用于启动静默 */
    val dismissedUpdateCode: Int = 0,
    /** 关掉更新的时刻（毫秒） */
    val dismissedUpdateAt: Long = 0,
)

object AppSettingsOptions {
    val COOLDOWN_MINUTES = listOf(1, 5, 10, 30, 60)
    val FAIL_THRESHOLDS = listOf(2, 3, 5, 10)
    val SLEEP_MINUTES = listOf(0, 15, 30, 60)
}
