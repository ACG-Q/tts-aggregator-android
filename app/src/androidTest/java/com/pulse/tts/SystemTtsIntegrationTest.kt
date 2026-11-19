package com.pulse.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.tts.data.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 系统 TTS 集成测试（真机/模拟器）：验证框架连续投递多段时，
 * 每一段都能完成合成并落到调用日志。
 *
 * 覆盖的回归：`onSynthesizeText` 提前返回会被框架视为本段已结束，
 * 下一段进来时取消上一段，最终只剩最后一段有声音。
 */
@RunWith(AndroidJUnit4::class)
class SystemTtsIntegrationTest {

    @Test
    fun 连续两段系统朗读都能完成合成() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val marker = System.currentTimeMillis().toString().takeLast(6)
        val first = "第一段测试$marker"
        val second = "第二段测试$marker"

        val init = CountDownLatch(1)
        var holder: TextToSpeech? = null
        inst.runOnMainSync {
            holder = TextToSpeech(
                context,
                { status -> if (status == TextToSpeech.SUCCESS) init.countDown() },
                context.packageName,
            )
        }
        assertTrue("TTS 引擎初始化失败", init.await(30, TimeUnit.SECONDS))
        val tts = requireNotNull(holder)

        val done = CountDownLatch(2)
        val errors = ConcurrentHashMap<String, String>()
        inst.runOnMainSync {
            tts.language = Locale.SIMPLIFIED_CHINESE
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    done.countDown()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    errors[utteranceId ?: "unknown"] = "unknown"
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    errors[utteranceId ?: "unknown"] = "code=$errorCode"
                }
            })
            tts.speak(first, TextToSpeech.QUEUE_ADD, null, "pulse-it-1")
            tts.speak(second, TextToSpeech.QUEUE_ADD, null, "pulse-it-2")
        }

        assertTrue("两段都应完成，实际 errors=$errors", done.await(180, TimeUnit.SECONDS))
        assertEquals(emptyMap<String, String>(), errors)

        val logs = awaitLogs(listOf(first, second), 20_000)
        listOf(first, second).forEach { text ->
            assertTrue(
                "日志缺少成功记录：$text；现有=${logs.map { it.textPreview to it.success }}",
                logs.any { it.success && it.textPreview.startsWith(text) },
            )
        }

        inst.runOnMainSync { tts.shutdown() }
    }

    private fun awaitLogs(texts: List<String>, timeoutMs: Long): List<CallLog> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deadline = System.currentTimeMillis() + timeoutMs
        var logs = emptyList<CallLog>()
        while (System.currentTimeMillis() < deadline) {
            logs = context.appContainer.logStore.logs.value
            if (texts.all { text -> logs.any { it.success && it.textPreview.startsWith(text) } }) {
                return logs
            }
            Thread.sleep(500)
        }
        return logs
    }
}
