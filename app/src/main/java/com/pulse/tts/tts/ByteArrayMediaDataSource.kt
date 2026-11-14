package com.pulse.tts.tts

import android.media.MediaDataSource
import java.io.IOException

/**
 * 把内存中的音频字节包装成 [MediaExtractor] 需要的随机读取数据源。
 *
 * 边界语义（返回 -1、末尾截断）放在纯 Kotlin 的 [ByteArrayRandomSource] 里，
 * 便于 JVM 单测；本类只做 Android 接口适配。
 */
internal class ByteArrayMediaDataSource(data: ByteArray) : MediaDataSource() {

    private val source = ByteArrayRandomSource(data)

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        source.readAt(position, buffer, offset, size)

    override fun getSize(): Long = source.size

    override fun close() = Unit
}

/**
 * 内存字节的随机读取，语义与 [MediaDataSource.readAt] 一致：
 * 读到末尾时返回实际字节数，position 越界返回 -1。
 */
internal class ByteArrayRandomSource(private val data: ByteArray) {

    val size: Long get() = data.size.toLong()

    @Throws(IOException::class)
    fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (offset < 0 || size < 0 || offset.toLong() + size > buffer.size) {
            throw IOException("读取越界：offset=$offset size=$size capacity=${buffer.size}")
        }
        if (position < 0 || position >= data.size) return -1
        val length = minOf(size.toLong(), data.size - position).toInt()
        if (length > 0) {
            System.arraycopy(data, position.toInt(), buffer, offset, length)
        }
        return length
    }
}
