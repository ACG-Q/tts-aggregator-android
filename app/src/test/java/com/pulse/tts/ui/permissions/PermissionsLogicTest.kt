package com.pulse.tts.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionsLogicTest {

    @Test
    fun `关键权限待处理数量只统计通知电池与后台`() {
        val states = listOf(
            PermissionState(PermissionKey.NOTIFY, granted = false),
            PermissionState(PermissionKey.BATTERY, granted = false),
            PermissionState(PermissionKey.BACKGROUND, granted = true),
            PermissionState(PermissionKey.NETWORK, granted = false),
            PermissionState(PermissionKey.TTS, granted = false),
            PermissionState(PermissionKey.FILES, granted = true),
        )

        assertEquals(2, pendingCriticalCount(states))
    }

    @Test
    fun `全部关键权限就绪时提示文案正确`() {
        val states = listOf(
            PermissionState(PermissionKey.NOTIFY, granted = true),
            PermissionState(PermissionKey.BATTERY, granted = true),
            PermissionState(PermissionKey.BACKGROUND, granted = true),
        )

        assertEquals("全部已就绪", permissionBadgeText(states))
    }

    @Test
    fun `存在待处理权限时显示数量`() {
        val states = listOf(
            PermissionState(PermissionKey.NOTIFY, granted = false),
            PermissionState(PermissionKey.BATTERY, granted = true),
            PermissionState(PermissionKey.BACKGROUND, granted = false),
        )

        assertEquals("2 项待处理", permissionBadgeText(states))
    }
}
