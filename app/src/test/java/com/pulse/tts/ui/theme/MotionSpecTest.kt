package com.pulse.tts.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionSpecTest {

    @Test
    fun `开启动效时保留基础时长`() {
        assertEquals(180, MotionSpec(enabled = true).duration(180))
    }

    @Test
    fun `关闭动效时时长归零`() {
        assertEquals(0, MotionSpec(enabled = false).duration(180))
    }
}
