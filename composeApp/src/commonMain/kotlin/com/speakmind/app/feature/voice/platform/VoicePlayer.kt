package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.StateFlow

expect class VoicePlayer() {
    suspend fun playFromBytes(audioData: ByteArray)
    suspend fun playFromUrl(url: String)
    fun pause()
    fun resume()
    /** Jumps to [fraction] (0..1) of the clip. */
    fun seekTo(fraction: Float)
    /** Playback speed, e.g. 1, 1.5 or 2. Kept for the next clip too. */
    fun setSpeed(speed: Float)
    fun stop()
    fun release()
    /** True while sound is coming out (false when paused or stopped). */
    val isPlaying: StateFlow<Boolean>
    val isPaused: StateFlow<Boolean>
    /** 0..1, updated many times a second while playing. */
    val progress: StateFlow<Float>
    val positionMs: StateFlow<Long>
}
