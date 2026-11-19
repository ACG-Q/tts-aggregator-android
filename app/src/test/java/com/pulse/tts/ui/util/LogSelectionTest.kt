package com.pulse.tts.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSelectionTest {

    @Test
    fun `toggle 在未选中时加入，已选中时移除`() {
        val once = toggleSelection(emptySet(), 7L)
        assertEquals(setOf(7L), once)
        assertEquals(emptySet<Long>(), toggleSelection(once, 7L))
    }

    @Test
    fun `invert 只保留可见项中的未选中者`() {
        val all = listOf(1L, 2L, 3L)
        assertEquals(setOf(2L), invertSelection(all, setOf(1L, 3L)))
        assertEquals(setOf(1L, 2L, 3L), invertSelection(all, emptySet()))
        assertEquals(emptySet<Long>(), invertSelection(all, setOf(1L, 2L, 3L)))
    }

    @Test
    fun `isAllSelected 在空列表时为 false`() {
        assertFalse(isAllSelected(emptyList(), emptySet()))
        assertFalse(isAllSelected(listOf(1L, 2L), setOf(1L)))
        assertTrue(isAllSelected(listOf(1L, 2L), setOf(1L, 2L, 3L)))
    }
}
