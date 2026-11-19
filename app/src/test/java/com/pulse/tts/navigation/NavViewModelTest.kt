package com.pulse.tts.navigation

import com.pulse.tts.data.CallLog
import com.pulse.tts.data.CallMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavViewModelTest {

    @Test
    fun `home 栈空 handleBack 返回 false`() {
        val nav = NavViewModel()
        assertEquals(Screen.Home, nav.screen.value)
        assertFalse(nav.handleBack())
        assertEquals(Screen.Home, nav.screen.value)
    }

    @Test
    fun `子页返回回上级 非首页`() {
        val nav = NavViewModel()
        nav.goNodes()
        assertTrue(nav.handleBack())
        assertEquals(Screen.Home, nav.screen.value)
        assertFalse(nav.handleBack())
    }

    @Test
    fun `多级导航按入栈顺序弹出`() {
        val nav = NavViewModel()
        nav.goSettings()
        nav.goAdmin()
        nav.goAbout()
        assertEquals(Screen.About, nav.screen.value)
        assertTrue(nav.handleBack())
        assertEquals(Screen.Admin, nav.screen.value)
        assertTrue(nav.handleBack())
        assertEquals(Screen.Settings, nav.screen.value)
        assertTrue(nav.handleBack())
        assertEquals(Screen.Home, nav.screen.value)
        assertFalse(nav.handleBack())
    }

    @Test
    fun `记录详情返回后回到记录页`() {
        val nav = NavViewModel()
        nav.goLogs()
        val log = CallLog(
            nodeUrl = "https://tts.example.com",
            mode = CallMode.AUTO,
            textLen = 4,
            attempt = 1,
            success = true,
            latencyMs = 120,
            textPreview = "你好世界",
        )

        nav.goLogDetail(log)
        assertEquals(Screen.LogDetail(log), nav.screen.value)
        assertTrue("详情页应能返回", nav.handleBack())
        assertEquals(Screen.Logs, nav.screen.value)
        assertEquals(MainTab.RECORDS, nav.tab.value)
    }

    @Test
    fun `goHome 清空栈 后续返回直接 false`() {
        val nav = NavViewModel()
        nav.goSettings()
        nav.goNodes()
        nav.goHome()
        assertEquals(Screen.Home, nav.screen.value)
        assertFalse(nav.handleBack())
    }
}
