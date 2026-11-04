package com.pulse.tts.playback

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackSnapshot(
    val title: String,
    val text: String,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
)
