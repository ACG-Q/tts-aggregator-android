package com.pulse.tts.data.lansync

import com.pulse.tts.data.TtsNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolTest {

    @Test
    fun `normalize drops default port and trailing slash`() {
        assertEquals("https://a.example.com", normalizeUrl("https://A.example.com:443/"))
        assertEquals("http://a.example.com/tts", normalizeUrl("http://a.example.com:80/tts/"))
        assertEquals("http://a.example.com:8080", normalizeUrl("http://a.example.com:8080/"))
    }

    @Test
    fun `normalize rejects non http`() {
        assertNull(normalizeUrl("ftp://a.example.com"))
        assertNull(normalizeUrl("a.example.com"))
        assertNull(normalizeUrl("   "))
    }

    @Test
    fun `merge adds new nodes without api key and keeps local secrets`() {
        val local = listOf(
            TtsNode(
                baseUrl = "https://a.example.com", label = "旧", apiKey = "secret",
                disabled = true, failCount = 2, group = "工作",
            ),
        )
        val remote = listOf(
            SyncedNode(url = "https://A.example.com/", label = "新", health = "FAIL", voiceCount = 9),
            SyncedNode(url = "https://b.example.com", label = "B", group = "家里", keyless = true),
            SyncedNode(url = "ftp://bad"),
        )
        val (nodes, stats) = mergeSyncedNodes(local, remote)

        assertEquals(MergeStats(added = 1, updated = 1, skipped = 1, total = 2), stats)
        val a = nodes.first { it.baseUrl == "https://a.example.com" }
        assertEquals("新", a.label)
        assertEquals("secret", a.apiKey)
        assertEquals(true, a.disabled)
        assertEquals(2, a.failCount)
        assertEquals("工作", a.group)
        val b = nodes.first { it.baseUrl == "https://b.example.com" }
        assertNull(b.apiKey)
        assertEquals("家里", b.group)
        assertEquals(true, b.keyless)
    }

    @Test
    fun `merge keeps local-only nodes`() {
        val local = listOf(TtsNode(baseUrl = "https://keep.example.com", id = "keep"))
        val (nodes, stats) = mergeSyncedNodes(local, emptyList())
        assertEquals(1, nodes.size)
        assertEquals("keep", nodes[0].id)
        assertEquals(MergeStats(0, 0, 0, 0), stats)
    }

    @Test
    fun `toSyncedNodes omits api key`() {
        val node = TtsNode(baseUrl = "https://a.example.com", apiKey = "secret", label = "A")
        val synced = toSyncedNodes(listOf(node)).single()
        assertEquals("https://a.example.com", synced.url)
        assertEquals("默认", synced.group)
        assertEquals("A", synced.label)
        assertTrue(synced.keyless != null)
    }

    @Test
    fun `pairing string round trips`() {
        val peer = SyncPeer("192.168.1.20", 53421, "ab12", "书房的电脑", "desktop")
        assertEquals(peer, parsePairingString(buildPairingString(peer)))
    }

    @Test
    fun `pairing string rejects invalid input`() {
        assertNull(parsePairingString("https://x"))
        assertNull(parsePairingString("pulsetts://sync?host=a&port=0&sid=x"))
        assertNull(parsePairingString("pulsetts://sync?host=a&port=1"))
    }
}
