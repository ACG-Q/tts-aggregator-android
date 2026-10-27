package com.example.ttsaggregator.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class NodeHealthRegistryTest {

    private fun node(
        id: String = "n",
        failCount: Int = 0,
        cooldownUntil: Long = 0,
    ) = TtsNode(
        id = id,
        baseUrl = "http://$id",
        failCount = failCount,
        cooldownUntil = cooldownUntil,
    )

    private fun registry(
        store: MutableMap<String, TtsNode>,
        clock: () -> Long = { 1000L },
    ) = NodeHealthRegistry(
        read = { store[it] },
        write = { store[it.id] = it },
        clock = clock,
    )

    @Test
    fun `连续失败三次触发十分钟冷却`() = runTest {
        val store = mutableMapOf("n" to node())
        val reg = registry(store)
        val snapshot = store.getValue("n")

        val first = reg.onFailure(snapshot, CallMode.AUTO)
        val second = reg.onFailure(snapshot, CallMode.AUTO)
        val third = reg.onFailure(snapshot, CallMode.AUTO)

        assertFalse(first.triggeredCooldown)
        assertFalse(second.triggeredCooldown)
        assertTrue(third.triggeredCooldown)
        assertEquals(3, third.failCount)
        assertEquals(1000L + 10 * 60 * 1000L, third.cooldownUntil)
        assertEquals(3, store.getValue("n").failCount)
        assertEquals(third.cooldownUntil, store.getValue("n").cooldownUntil)
    }

    @Test
    fun `自定义阈值与冷却时长生效`() = runTest {
        val store = mutableMapOf("n" to node())
        val reg = NodeHealthRegistry(
            read = { store[it] },
            write = { store[it.id] = it },
            clock = { 1000L },
            failThreshold = { 2 },
            cooldownMs = { 60_000L },
        )
        val snapshot = store.getValue("n")

        val first = reg.onFailure(snapshot, CallMode.AUTO)
        val second = reg.onFailure(snapshot, CallMode.AUTO)

        assertFalse(first.triggeredCooldown)
        assertTrue(second.triggeredCooldown)
        assertEquals(61_000L, second.cooldownUntil)
    }

    @Test
    fun `手选模式累计失败但不触发冷却`() = runTest {
        val store = mutableMapOf("n" to node())
        val reg = registry(store)
        val snapshot = store.getValue("n")

        repeat(3) { reg.onFailure(snapshot, CallMode.MANUAL) }

        assertEquals(3, store.getValue("n").failCount)
        assertEquals(0L, store.getValue("n").cooldownUntil)
    }

    @Test
    fun `成功后清零失败计数与冷却`() = runTest {
        val store = mutableMapOf("n" to node(failCount = 2, cooldownUntil = 500))
        val reg = registry(store)

        reg.onSuccess(store.getValue("n"))

        assertEquals(0, store.getValue("n").failCount)
        assertEquals(0L, store.getValue("n").cooldownUntil)
    }

    @Test
    fun `并发失败不丢计数`() = runTest {
        val store = ConcurrentHashMap<String, TtsNode>()
        store["n"] = node()
        val reg = registry(store)
        val snapshot = store.getValue("n")

        coroutineScope {
            repeat(5) {
                launch { reg.onFailure(snapshot, CallMode.AUTO) }
            }
        }

        assertEquals(5, store.getValue("n").failCount)
    }
}
