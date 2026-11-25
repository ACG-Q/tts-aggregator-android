package com.pulse.tts.data

import com.pulse.tts.BuildConfig
import kotlinx.serialization.Serializable

/** 明暗样式：只决定界面深浅，不决定视觉风格。 */
@Serializable
enum class ThemeStyle {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** 视觉风格：与明暗样式独立组合。 */
@Serializable
enum class ThemeSkin {
    NORMAL,
    INK,
    GLASS,
}

/** 更新渠道：正式版只收正式发布，全部版本包含预发布。 */
@Serializable
enum class UpdateChannel {
    STABLE,
    ALL,
}

/**
 * 首次安装的默认主题。
 *
 * 常规构建跟随系统 + 常规主题；以 `-PpulseDefaultTheme=ink` 构建时默认墨水屏，
 * 但同样跟随系统明暗。升级安装不会改动用户已保存的主题。
 */
object ThemeDefaults {
    val style: ThemeStyle = ThemeStyle.SYSTEM
    val skin: ThemeSkin get() = skinFor(BuildConfig.DEFAULT_INK_THEME)

    internal fun skinFor(inkDefault: Boolean): ThemeSkin =
        if (inkDefault) ThemeSkin.INK else ThemeSkin.NORMAL
}

@Serializable
data class AppSettings(
    val themeStyle: ThemeStyle = ThemeDefaults.style,
    val themeSkin: ThemeSkin = ThemeDefaults.skin,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val backgroundPlay: Boolean = true,
    val resume: Boolean = true,
    val autoTestOnLaunch: Boolean = true,
    val cooldownMinutes: Int = 10,
    val failThreshold: Int = 3,
    val sleepMinutes: Int = 0,
    /** 朗读音频缓存保留天数，0 = 关闭 */
    val audioCacheDays: Int = 7,
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
    val AUDIO_CACHE_DAYS = listOf(0, 1, 7, 30)
}
