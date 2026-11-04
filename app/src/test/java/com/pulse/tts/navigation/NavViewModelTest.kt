package com.pulse.tts.navigation

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
    fun `goHome 清空栈 后续返回直接 false`() {
        val nav = NavViewModel()
        nav.goSettings()
        nav.goNodes()
        nav.goHome()
        assertEquals(Screen.Home, nav.screen.value)
        assertFalse(nav.handleBack())
    }
}
