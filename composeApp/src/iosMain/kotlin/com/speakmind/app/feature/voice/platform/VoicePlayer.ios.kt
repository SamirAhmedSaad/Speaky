package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class VoicePlayer actual constructor() {

    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    actual val progress: StateFlow<Float> = _progress.asStateFlow()

    actual suspend fun playFromBytes(audioData: ByteArray) {
        // TODO: Implement with AVAudioPlayer
    }

    actual suspend fun playFromUrl(url: String) {
        // TODO: Implement with AVAudioPlayer
    }

    private val _isPaused = MutableStateFlow(false)
    actual val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    actual val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    actual fun pause() {}
    actual fun resume() {}
    actual fun seekTo(fraction: Float) {}
    actual fun setSpeed(speed: Float) {}

    actual fun stop() {
        _isPlaying.value = false
        _progress.value = 0f
    }

    actual fun release() {
        stop()
    }
}
