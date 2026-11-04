package com.pulse.tts.playback

import com.pulse.tts.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSnapshotTest {

    @Test
    fun encodeAndDecode_roundTripsAllFields() {
        val snapshot = PlaybackSnapshot(
            title = "标题",
            text = "正文",
            positionMs = 12_345,
            durationMs = 60_000,
            speed = 1.2f,
        )

        val raw = SettingsStore.encodePlaybackSnapshot(snapshot)
        val restored = SettingsStore.decodePlaybackSnapshot(raw)

        assertEquals(snapshot, restored)
    }

    @Test
    fun decode_returnsNullForCorruptJson() {
        assertNull(SettingsStore.decodePlaybackSnapshot("{bad"))
    }
}
