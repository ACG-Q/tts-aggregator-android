package com.pulse.tts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    private val manifest = """
        {
          "versionName": "1.0.5",
          "versionCode": 10005,
          "minSupportedVersionCode": 10000,
          "releasePage": "https://github.com/acg-q/tts-aggregator-android/releases/tag/v1.0.5",
          "notes": "修复若干问题",
          "apks": {
            "arm64-v8a": "https://example.com/arm64.apk",
            "universal": "https://example.com/universal.apk"
          },
          "sha256": { "arm64-v8a": "abc" },
          "unknownField": true
        }
    """.trimIndent()

    @Test
    fun `解析清单并忽略未知字段`() {
        val info = requireNotNull(parseUpdateInfo(manifest))
        assertEquals("1.0.5", info.versionName)
        assertEquals(10005, info.versionCode)
        assertEquals(10000, info.minSupportedVersionCode)
        assertEquals("abc", info.sha256["arm64-v8a"])
    }

    @Test
    fun `非法清单返回 null`() {
        assertNull(parseUpdateInfo(""))
        assertNull(parseUpdateInfo("not json"))
        assertNull(parseUpdateInfo("""{"versionCode": 0}"""))
    }

    @Test
    fun `低于最低支持版本时强制更新`() {
        val info = UpdateInfo(versionCode = 10005, minSupportedVersionCode = 10000)

        assertTrue(decideUpdate(9999, info) is UpdateDecision.Required)
        assertTrue(decideUpdate(10000, info) is UpdateDecision.Optional)
        assertTrue(decideUpdate(10001, info) is UpdateDecision.Optional)
    }

    @Test
    fun `达到最新版本时不需要更新`() {
        val info = UpdateInfo(versionCode = 10005, minSupportedVersionCode = 0)

        assertEquals(UpdateDecision.UpToDate, decideUpdate(10005, info))
        assertEquals(UpdateDecision.UpToDate, decideUpdate(10010, info))
        assertEquals(UpdateDecision.UpToDate, decideUpdate(1, null))
    }

    @Test
    fun `最低支持版本为 0 时从不强制`() {
        val info = UpdateInfo(versionCode = 10005, minSupportedVersionCode = 0)

        assertTrue(decideUpdate(1, info) is UpdateDecision.Optional)
    }

    @Test
    fun `加速地址为空表示直连`() {
        assertEquals("", normalizeGithubProxy(""))
        assertEquals("", normalizeGithubProxy("   "))
        assertEquals("https://github.com/a", buildProxiedUrl("", "https://github.com/a"))
    }

    @Test
    fun `加速地址自动补尾部斜杠`() {
        assertEquals("https://ghproxy.net/", normalizeGithubProxy("https://ghproxy.net"))
        assertEquals("https://ghproxy.net/", normalizeGithubProxy(" https://ghproxy.net/ "))
        assertEquals(
            "https://ghproxy.net/https://github.com/a",
            buildProxiedUrl("https://ghproxy.net", "https://github.com/a"),
        )
    }

    @Test
    fun `拒绝非 http 协议与含空白的加速地址`() {
        assertNull(normalizeGithubProxy("ftp://ghproxy.net"))
        assertNull(normalizeGithubProxy("javascript:alert(1)"))
        assertNull(normalizeGithubProxy("ghproxy.net"))
        assertNull(normalizeGithubProxy("https://gh proxy.net"))
        assertFalse(isValidGithubProxy("https://"))
    }

    @Test
    fun `按设备 ABI 优先选择安装包`() {
        val info = UpdateInfo(
            apks = mapOf(
                "arm64-v8a" to "https://example.com/arm64.apk",
                "universal" to "https://example.com/universal.apk",
            ),
        )

        val target = requireNotNull(pickApk(info, listOf("x86_64", "arm64-v8a")))
        assertEquals("arm64-v8a", target.abi)
        assertEquals("https://example.com/arm64.apk", target.url)
    }

    @Test
    fun `没有匹配 ABI 时回退 universal`() {
        val info = UpdateInfo(
            apks = mapOf(
                "x86" to "https://example.com/x86.apk",
                "universal" to "https://example.com/universal.apk",
            ),
        )

        val target = requireNotNull(pickApk(info, listOf("arm64-v8a")))
        assertEquals("universal", target.abi)
    }

    @Test
    fun `启动检查节流`() {
        val now = 1_000_000_000L

        assertTrue(shouldCheckOnLaunch(lastCheckAt = 0L, now = now))
        assertFalse(shouldCheckOnLaunch(lastCheckAt = now - 1000, now = now))
        assertTrue(shouldCheckOnLaunch(lastCheckAt = now - UPDATE_CHECK_INTERVAL_MS, now = now))
        // 系统时间回拨时允许重新检查
        assertTrue(shouldCheckOnLaunch(lastCheckAt = now + 1000, now = now))
    }
}
