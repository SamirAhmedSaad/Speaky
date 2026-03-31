package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.StateFlow

expect class TextToSpeechEngine() {
    suspend fun speak(text: String, rate: Float = 1.0f, language: String = "en-US")
    fun stop()
    fun isAvailable(): Boolean
    val isSpeaking: StateFlow<Boolean>
}
