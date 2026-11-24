package com.pulse.tts.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsChannelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `旧配置缺少渠道字段时使用正式版默认值`() {
        val decoded = json.decodeFromString(AppSettings.serializer(), """{"theme":"DARK"}""")

        assertEquals(UpdateChannel.STABLE, decoded.updateChannel)
        assertEquals(0, decoded.dismissedUpdateCode)
        assertEquals(0L, decoded.dismissedUpdateAt)
    }

    @Test
    fun `渠道与忽略记录可以往返序列化`() {
        val original = AppSettings(
            updateChannel = UpdateChannel.ALL,
            dismissedUpdateCode = 101001,
            dismissedUpdateAt = 1_700_000_000_000L,
        )

        val raw = json.encodeToString(AppSettings.serializer(), original)
        val roundTrip = json.decodeFromString(AppSettings.serializer(), raw)

        assertEquals(original, roundTrip)
    }
}
