package com.pulse.tts.ui.theme

import com.pulse.tts.data.ThemeSkin
import com.pulse.tts.data.ThemeStyle

/** 设置首页与外观页共用的主题文案，保证两处始终一致。 */

internal fun styleLabel(style: ThemeStyle): String = when (style) {
    ThemeStyle.SYSTEM -> "跟随系统"
    ThemeStyle.LIGHT -> "浅色"
    ThemeStyle.DARK -> "深色"
}

internal fun skinLabel(skin: ThemeSkin): String = when (skin) {
    ThemeSkin.NORMAL -> "常规"
    ThemeSkin.INK -> "墨水屏"
    ThemeSkin.GLASS -> "液态玻璃"
}

/** 单行摘要：常规主题只显示样式，特殊主题在自选明暗时附上样式。 */
internal fun themeSummary(style: ThemeStyle, skin: ThemeSkin): String = when {
    skin == ThemeSkin.NORMAL && style == ThemeStyle.SYSTEM -> "跟随系统"
    skin == ThemeSkin.NORMAL -> styleLabel(style)
    style == ThemeStyle.SYSTEM -> skinLabel(skin)
    else -> "${skinLabel(skin)} · ${styleLabel(style)}"
}
