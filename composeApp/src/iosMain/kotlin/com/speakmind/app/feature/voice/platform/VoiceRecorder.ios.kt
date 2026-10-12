package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class VoiceRecorder actual constructor() {

    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitudes = MutableStateFlow(0f)
    actual val amplitudes: StateFlow<Float> = _amplitudes.asStateFlow()

    actual fun startRecording() {
        // TODO: Implement with AVAudioRecorder
        _isRecording.value = true
    }

    actual fun stopRecording(): ByteArray {
        _isRecording.value = false
        _amplitudes.value = 0f
        return ByteArray(0)
    }

    actual fun recordedLevels(bars: Int): List<Float> = emptyList()

    actual fun cancelRecording() {
        _isRecording.value = false
        _amplitudes.value = 0f
    }
}
