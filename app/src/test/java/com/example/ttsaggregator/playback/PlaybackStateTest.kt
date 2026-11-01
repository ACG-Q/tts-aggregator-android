package com.example.ttsaggregator.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun isCompleted_trueWhenPlaybackReachedEnd() {
        val state = PlaybackState(
            isActive = true,
            isPlaying = false,
            positionMs = 8_000L,
            durationMs = 8_000L,
        )
        assertTrue(state.isCompleted)
    }

    @Test
    fun isCompleted_trueWhenWithinCompletionTolerance() {
        val state = PlaybackState(
            isActive = true,
            isPlaying = false,
            positionMs = 7_800L,
            durationMs = 8_000L,
        )
        assertTrue(state.isCompleted)
    }

    @Test
    fun isCompleted_falseWhilePlaying() {
        val state = PlaybackState(
            isActive = true,
            isPlaying = true,
            positionMs = 8_000L,
            durationMs = 8_000L,
        )
        assertFalse(state.isCompleted)
    }

    @Test
    fun isCompleted_falseBeforeEnd() {
        val state = PlaybackState(
            isActive = true,
            isPlaying = false,
            positionMs = 2_000L,
            durationMs = 8_000L,
        )
        assertFalse(state.isCompleted)
    }

    @Test
    fun isCompleted_falseWhenInactiveOrWithoutDuration() {
        assertFalse(PlaybackState(isActive = false, positionMs = 8_000L, durationMs = 8_000L).isCompleted)
        assertFalse(PlaybackState(isActive = true, positionMs = 0L, durationMs = 0L).isCompleted)
    }
}
