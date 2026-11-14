package com.pulse.tts.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream

/**
 * MP3 → PCM 16bit 解码器（基于系统 MediaExtractor + MediaCodec，零第三方依赖）。
 *
 * 系统 TTS 的 SynthesisCallback 只接受 PCM 数据，而 TTS 服务端只返回 MP3，
 * 因此合成后需先解码。
 *
 * 不能把整段 MP3 字节流直接喂给 MediaCodec：MP3 解码器按帧解析输入，
 * 未对齐帧边界的数据在部分实现上会直接报 err -14 并终止会话。
 * 这里先用 MediaExtractor 拆出帧，再逐 sample 送入解码器，
 * ID3 标签、变码率等都由 Extractor 处理。
 */
internal object Mp3Decoder {

    data class PcmResult(
        val pcm: ByteArray,
        val sampleRate: Int,
        val channelCount: Int,
    )

    fun decode(mp3: ByteArray): PcmResult {
        require(mp3.isNotEmpty()) { "MP3 数据为空" }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(ByteArrayMediaDataSource(mp3))
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("MP3 解码失败：未找到音频轨道")
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: error("MP3 解码失败：音频轨道缺少 MIME")
            return decodeTrack(extractor, format, mime)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun decodeTrack(
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String,
    ): PcmResult {
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val out = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var completed = false
            var sampleRate = 0
            var channelCount = 0
            val deadline = System.currentTimeMillis() + MAX_DECODE_MS

            loop@ while (!completed) {
                // 硬性超时：数据损坏/解码器不吐 EOS 时避免无限空转
                if (System.currentTimeMillis() > deadline) error("MP3 解码超时")

                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                            ?: error("MP3 解码失败：输入缓冲区不可用")
                        inBuf.clear()
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (sampleRate == 0) {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(outIndex)
                                ?: error("MP3 解码失败：输出缓冲区不可用")
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val chunk = ByteArray(info.size)
                            buf.get(chunk)
                            out.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            completed = true
                            break@loop
                        }
                    }
                }
            }

            check(completed) { "MP3 解码未完成（超时或数据损坏）" }
            check(sampleRate > 0) { "MP3 解码失败：未获取输出格式" }
            check(out.size() > 0) { "MP3 解码失败：没有输出 PCM 数据" }
            return PcmResult(out.toByteArray(), sampleRate, channelCount)
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private const val TIMEOUT_US = 10_000L
    private const val MAX_DECODE_MS = 30_000L
}
