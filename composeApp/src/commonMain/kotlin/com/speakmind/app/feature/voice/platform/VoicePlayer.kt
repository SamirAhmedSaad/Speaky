package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.StateFlow

expect class VoicePlayer() {
    suspend fun playFromBytes(audioData: ByteArray)
    suspend fun playFromUrl(url: String)
    fun stop()
    fun release()
    val isPlaying: StateFlow<Boolean>
    val progress: StateFlow<Float>
}
