package com.example.ttsaggregator.tts

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.ByteArrayOutputStream

/**
 * MP3 → PCM 16bit 解码器（基于系统 MediaCodec，零第三方依赖）。
 *
 * 系统 TTS 的 SynthesisCallback 只接受 PCM 数据，
 * 而 TTS 服务端只返回 MP3，因此合成后需先解码。
 */
internal object Mp3Decoder {

    data class PcmResult(
        val pcm: ByteArray,
        val sampleRate: Int,
        val channelCount: Int,
    )

    fun decode(mp3: ByteArray): PcmResult {
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG)
        // 占位参数：真实采样率/声道由 MP3 头决定，输出格式以 codec.outputFormat 为准
        val dummy = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_MPEG, 24000, 2)
        codec.configure(dummy, null, null, 0)
        codec.start()

        val out = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var offset = 0
        var inputDone = false
        var sampleRate = 0
        var channelCount = 0

        try {
            loop@ while (true) {
                // 1) 喂输入
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val len = minOf(inBuf.capacity(), mp3.size - offset)
                        if (len > 0) {
                            inBuf.put(mp3, offset, len)
                            offset += len
                        }
                        val eos = offset >= mp3.size
                        codec.queueInputBuffer(
                            inIndex, 0, len, 0,
                            if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0,
                        )
                        if (eos) inputDone = true
                    }
                }

                // 2) 取输出
                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 输入已尽但迟迟无 EOS → 兜底退出，防止死循环
                        if (inputDone && out.size() > 0) break@loop
                    }
                    else -> {
                        if (sampleRate == 0) {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(outIndex)!!
                            buf.position(info.offset)
                            val pcm = ByteArray(info.size)
                            buf.get(pcm)
                            out.write(pcm)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        check(sampleRate > 0) { "MP3 解码失败：未获取输出格式" }
        return PcmResult(out.toByteArray(), sampleRate, channelCount)
    }

    private const val TIMEOUT_US = 10_000L
}
