package com.pulse.tts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelLogicTest {

    private val releasesJson = """
        [
          {
            "tag_name": "v1.1.0-beta.1",
            "draft": false,
            "assets": [
              {"name": "update.json", "browser_download_url": "https://example.com/beta/update.json"}
            ]
          },
          {
            "tag_name": "v1.0.8",
            "draft": false,
            "assets": [
              {"name": "Pulse-v1.0.8.apk", "browser_download_url": "https://example.com/1.0.8.apk"},
              {"name": "update.json", "browser_download_url": "https://example.com/stable/update.json"}
            ]
          },
          {
            "tag_name": "v9.9.9",
            "draft": true,
            "assets": [
              {"name": "update.json", "browser_download_url": "https://example.com/draft/update.json"}
            ]
          },
          {
            "tag_name": "v0.9.0",
            "draft": false,
            "assets": []
          }
        ]
    """.trimIndent()

    @Test
    fun `解析 Release 列表并忽略未知字段`() {
        val releases = parseReleaseList(releasesJson)

        assertEquals(4, releases.size)
        assertEquals("v1.1.0-beta.1", releases[0].tagName)
        assertEquals(1, releases[0].assets.size)
    }

    @Test
    fun `损坏的 JSON 返回空列表`() {
        assertTrue(parseReleaseList("not json").isEmpty())
        assertTrue(parseReleaseList("").isEmpty())
        assertTrue(parseReleaseList("""{"draft":false}""").isEmpty())
    }

    @Test
    fun `候选清单跳过 draft 与缺少 update json 的条目`() {
        val candidates = selectManifestCandidates(parseReleaseList(releasesJson))

        assertEquals(
            listOf("https://example.com/beta/update.json", "https://example.com/stable/update.json"),
            candidates,
        )
    }

    @Test
    fun `候选清单最多取指定数量`() {
        val releases = (1..8).map { index ->
            GithubRelease(
                tagName = "v1.0.$index",
                assets = listOf(GithubAsset("update.json", "https://example.com/$index.json")),
            )
        }

        assertEquals(5, selectManifestCandidates(releases).size)
        assertEquals(2, selectManifestCandidates(releases, limit = 2).size)
        assertTrue(selectManifestCandidates(releases, limit = 0).isEmpty())
    }

    @Test
    fun `候选选择忽略空地址`() {
        val releases = listOf(
            GithubRelease(assets = listOf(GithubAsset("update.json", "   "))),
            GithubRelease(assets = listOf(GithubAsset("update.json", "https://example.com/ok.json"))),
        )

        assertEquals(listOf("https://example.com/ok.json"), selectManifestCandidates(releases))
    }

    @Test
    fun `选取 versionCode 最大的清单`() {
        val infos = listOf(
            UpdateInfo(versionCode = 10008),
            UpdateInfo(versionCode = 101001),
            UpdateInfo(versionCode = 0),
        )

        assertEquals(101001, pickNewestInfo(infos)?.versionCode)
        assertNull(pickNewestInfo(emptyList()))
    }

    @Test
    fun `启动决策跳过已最新与静默版本`() {
        val now = 1_000_000L
        val info = UpdateInfo(versionCode = 101001, minSupportedVersionCode = 0)

        assertEquals(
            UpdateDecision.Optional(info),
            decideLaunchUpdate(100089, info, dismissedCode = 0, dismissedAt = 0, now = now),
        )
        assertNull(
            decideLaunchUpdate(101001, info, dismissedCode = 0, dismissedAt = 0, now = now),
        )
        assertNull(
            decideLaunchUpdate(100089, info, dismissedCode = 101001, dismissedAt = now - 1000, now = now),
        )
        assertEquals(
            UpdateDecision.Optional(info),
            decideLaunchUpdate(
                100089,
                info,
                dismissedCode = 101001,
                dismissedAt = now - UPDATE_CHECK_INTERVAL_MS,
                now = now,
            ),
        )
    }

    @Test
    fun `启动决策保持强制更新优先`() {
        val now = 1_000_000L
        val info = UpdateInfo(versionCode = 101001, minSupportedVersionCode = 100089)

        assertEquals(
            UpdateDecision.Required(info),
            decideLaunchUpdate(1, info, dismissedCode = 101001, dismissedAt = now, now = now),
        )
    }

    @Test
    fun `同版本在间隔内静默`() {
        val now = 1_000_000L

        assertFalse(shouldSilenceDismissal(0, 0, 101001, now))
        assertFalse(shouldSilenceDismissal(101001, 0, 101001, now))
        assertTrue(shouldSilenceDismissal(101001, now - 1, 101001, now))
        assertTrue(shouldSilenceDismissal(101001, now + 5000, 101001, now))
        assertFalse(shouldSilenceDismissal(101001, now - UPDATE_CHECK_INTERVAL_MS, 101001, now))
        assertFalse(shouldSilenceDismissal(101001, now - 1000, 101002, now))
    }
}
