package com.pulse.tts.playback

import kotlin.math.max

/**
 * 朗读进度与文本的纯计算工具：不依赖 Android，便于单元测试。
 * 句子高亮按「字符数」近似映射播放进度，与 TTS 的实际音素切分无关。
 */
object ReadingTimeline {

    private val SENTENCE_END = charArrayOf('。', '！', '？', '!', '?', '；', ';', '\n')

    fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val sentences = mutableListOf<String>()
        val buffer = StringBuilder()
        text.trim().forEach { ch ->
            buffer.append(ch)
            if (ch in SENTENCE_END) {
                buffer.toString().trim().takeIf { it.isNotEmpty() }?.let(sentences::add)
                buffer.clear()
            }
        }
        buffer.toString().trim().takeIf { it.isNotEmpty() }?.let(sentences::add)
        return sentences
    }

    fun indexAt(sentences: List<String>, progress: Float): Int {
        if (sentences.isEmpty()) return 0
        val total = sentences.sumOf { it.length }.toFloat()
        if (total <= 0f) return 0
        val target = progress.coerceIn(0f, 1f) * total
        var acc = 0
        sentences.forEachIndexed { index, sentence ->
            acc += sentence.length
            if (target <= acc) return index
        }
        return sentences.lastIndex
    }

    fun sentenceAt(sentences: List<String>, progress: Float): String {
        if (sentences.isEmpty()) return ""
        return sentences[indexAt(sentences, progress)]
    }

    fun format(ms: Long): String {
        val seconds = max(0L, ms / 1000)
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

/**
 * 首页语速/音调控件的枚举与标签，避免 UI 与存储层各自维护一份映射。
 * rate/pitch 使用节点接口的 -100..100 语义。
 */
object ReadTuning {
    val RATES = listOf(-20, 0, 20, 50)
    val PITCHES = listOf(-20, 0, 20)

    fun nextRate(current: Int): Int {
        val index = RATES.indexOf(current)
        return if (index < 0) RATES[1] else RATES[(index + 1) % RATES.size]
    }

    fun nextPitch(current: Int): Int {
        val index = PITCHES.indexOf(current)
        return if (index < 0) PITCHES[1] else PITCHES[(index + 1) % PITCHES.size]
    }

    fun rateLabel(rate: Int): String = when (rate) {
        -20 -> "0.8×"
        20 -> "1.2×"
        50 -> "1.5×"
        else -> "1.0×"
    }

    fun pitchLabel(pitch: Int): String = when {
        pitch < 0 -> "低"
        pitch > 0 -> "高"
        else -> "标准"
    }

    /** 把界面语速换算成 MediaPlayer 的播放倍率（仅用于试听）。 */
    fun playbackSpeed(rate: Int): Float = when {
        rate <= -20 -> 0.8f
        rate >= 50 -> 1.5f
        rate >= 20 -> 1.2f
        else -> 1.0f
    }
}
