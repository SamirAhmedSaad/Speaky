package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.StateFlow

expect class VoiceRecorder() {
    fun startRecording()
    fun stopRecording(): ByteArray
    fun cancelRecording()
    val isRecording: StateFlow<Boolean>
    val amplitudes: StateFlow<Float>
}
