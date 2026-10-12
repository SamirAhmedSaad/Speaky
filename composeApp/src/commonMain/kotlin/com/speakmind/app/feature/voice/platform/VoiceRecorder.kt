package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.StateFlow

expect class VoiceRecorder() {
    fun startRecording()
    fun stopRecording(): ByteArray
    fun cancelRecording()
    val isRecording: StateFlow<Boolean>
    /** Current input loudness, 0..1, updated several times a second while recording. */
    val amplitudes: StateFlow<Float>
    /** The last recording's loudness squeezed into [bars] values (0..1), for its waveform. */
    fun recordedLevels(bars: Int): List<Float>
}
