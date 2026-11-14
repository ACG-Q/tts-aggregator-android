package com.pulse.tts.tts

import java.util.Locale
import java.util.MissingResourceException

/**
 * 系统 TTS 引擎对外声明的语言/音色元数据。
 *
 * 框架会用 [onIsLanguageAvailable] 的返回值反推 locale 形状
 * （见 TextToSpeechService.getExpectedLanguageAvailableStatus）：只有返回码与请求的
 * lang/country/variant 组合一致，该 locale 才会被认定为可用。
 * 返回值不一致时，系统设置会把引擎的默认语言判为不支持，
 * 从而跳过 GET_SAMPLE_TEXT，「播放」按钮因此没有试听文本可用。
 *
 * 返回码与 TextToSpeech 的常量数值一致，这里单独声明以便 JVM 单测。
 */
internal object TtsLanguageSupport {

    /** 引擎唯一音色名（BCP-47）；同时作为任意 locale 的默认音色返回 */
    const val VOICE_NAME = "zh-CN"

    const val LANG_NOT_SUPPORTED = -2
    const val LANG_MISSING_DATA = -1
    const val LANG_AVAILABLE = 0
    const val LANG_COUNTRY_AVAILABLE = 1
    const val LANG_COUNTRY_VAR_AVAILABLE = 2

    /**
     * 云端合成不限制语种，因此只按请求的 locale 形状返回对应的可用级别。
     *
     * @return [LANG_NOT_SUPPORTED] 当语言码缺失；否则
     *   [LANG_COUNTRY_VAR_AVAILABLE] / [LANG_COUNTRY_AVAILABLE] / [LANG_AVAILABLE]
     */
    fun availabilityOf(lang: String?, country: String?, variant: String?): Int = when {
        lang.isNullOrEmpty() -> LANG_NOT_SUPPORTED
        !variant.isNullOrEmpty() -> LANG_COUNTRY_VAR_AVAILABLE
        !country.isNullOrEmpty() -> LANG_COUNTRY_AVAILABLE
        else -> LANG_AVAILABLE
    }

    /**
     * 生成 CHECK_TTS_DATA 的可用语言列表（ISO3 形式）。
     *
     * 系统设置会把引擎的默认语言与这个列表逐项比对，不在列表里就判定为不支持，
     * 进而跳过 GET_SAMPLE_TEXT，导致「播放」按钮没有试听文本。
     * 云端合成不限语种，因此把当前默认语言和中文一起声明即可。
     *
     * @return 去重后的 `lang[-COUNTRY[-VARIANT]]` 列表，缺失 ISO3 码的 locale 会被忽略
     */
    fun availableVoiceTags(locales: List<Locale>): List<String> {
        val tags = mutableListOf<String>()
        for (locale in locales) {
            val tag = try {
                buildString {
                    append(locale.getISO3Language())
                    if (locale.country.isNotEmpty()) append('-').append(locale.getISO3Country())
                    if (locale.variant.isNotEmpty()) append('-').append(locale.variant)
                }
            } catch (e: MissingResourceException) {
                continue
            }
            if (tag.isNotEmpty() && tag !in tags) tags += tag
        }
        return tags
    }
}
