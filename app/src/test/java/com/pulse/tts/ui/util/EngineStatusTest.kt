package com.pulse.tts.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineStatusTest {

    @Test
    fun `非默认引擎显示未启用`() {
        assertEquals(EngineStatus.DISABLED, engineStatus(isDefaultEngine = false, serviceRunning = true))
        assertEquals("未启用", engineStatusLabel(EngineStatus.DISABLED))
    }

    @Test
    fun `默认引擎且服务存活显示运行中`() {
        assertEquals(EngineStatus.ACTIVE, engineStatus(isDefaultEngine = true, serviceRunning = true))
        assertEquals("服务运行中", engineStatusLabel(EngineStatus.ACTIVE))
    }

    @Test
    fun `默认引擎但服务未启动显示就绪`() {
        assertEquals(EngineStatus.READY, engineStatus(isDefaultEngine = true, serviceRunning = false))
        assertEquals("引擎就绪", engineStatusLabel(EngineStatus.READY))
    }
}
