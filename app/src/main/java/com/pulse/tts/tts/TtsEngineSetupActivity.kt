package com.pulse.tts.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.pulse.tts.R

/**
 * 系统「文字转语音输出」页的引擎自检与试听入口。
 *
 * 语音数据在云端，没有本地语言包，所以直接返回「数据就绪」并声明支持中文；
 * 缺少这两个 intent 时，系统设置会把语言、语速、试听按钮全部置灰。
 */
class TtsEngineSetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val result = Intent()
        when (intent?.action) {
            TextToSpeech.Engine.ACTION_CHECK_TTS_DATA -> {
                result.putStringArrayListExtra(
                    TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                    // 系统设置按 ISO3 比对默认语言（zh_CN → zho-CHN），这里不能写 zh-CN
                    arrayListOf("zho-CHN"),
                )
                setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
            }

            TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT -> {
                result.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, getString(R.string.test_text))
                // 系统设置只认 LANG_AVAILABLE 作为「试听文本可用」的返回码
                setResult(TextToSpeech.LANG_AVAILABLE, result)
            }
        }
        finish()
    }
}
