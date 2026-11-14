package com.pulse.tts.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ByteArrayRandomSourceTest {

    private val data = byteArrayOf(1, 2, 3, 4, 5)

    @Test
    fun `size 返回字节数`() {
        assertEquals(5L, ByteArrayRandomSource(data).size)
    }

    @Test
    fun `从头读取全部数据`() {
        val buffer = ByteArray(5)
        assertEquals(5, ByteArrayRandomSource(data).readAt(0, buffer, 0, 5))
        assertArrayEquals(data, buffer)
    }

    @Test
    fun `从中间读取到末尾自动截断`() {
        val buffer = ByteArray(8)
        assertEquals(3, ByteArrayRandomSource(data).readAt(2, buffer, 0, 8))
        assertArrayEquals(byteArrayOf(3, 4, 5, 0, 0, 0, 0, 0), buffer)
    }

    @Test
    fun `带偏移写入缓冲区`() {
        val buffer = ByteArray(5)
        assertEquals(2, ByteArrayRandomSource(data).readAt(1, buffer, 3, 2))
        assertArrayEquals(byteArrayOf(0, 0, 0, 2, 3), buffer)
    }

    @Test
    fun `读取长度为 0 时返回 0`() {
        assertEquals(0, ByteArrayRandomSource(data).readAt(0, ByteArray(0), 0, 0))
    }

    @Test
    fun `位置等于或超过末尾时返回 -1`() {
        val source = ByteArrayRandomSource(data)
        assertEquals(-1, source.readAt(5, ByteArray(1), 0, 1))
        assertEquals(-1, source.readAt(100, ByteArray(1), 0, 1))
    }

    @Test
    fun `负数位置返回 -1`() {
        assertEquals(-1, ByteArrayRandomSource(data).readAt(-1, ByteArray(1), 0, 1))
    }

    @Test(expected = IOException::class)
    fun `缓冲区越界抛出 IO 异常`() {
        ByteArrayRandomSource(data).readAt(0, ByteArray(2), 1, 2)
    }
}
