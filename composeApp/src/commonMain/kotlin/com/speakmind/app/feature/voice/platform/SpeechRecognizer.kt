package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.StateFlow

sealed class SpeechResult {
    data object Idle : SpeechResult()
    data class Partial(val text: String) : SpeechResult()
    data class Final(val text: String, val confidence: Float = 1f) : SpeechResult()
    data class Error(val message: String) : SpeechResult()
}

expect class SpeechRecognizerEngine() {
    fun startListening(language: String = "en-US")
    fun stopListening()
    fun isAvailable(): Boolean
    val results: StateFlow<SpeechResult>
    val isListening: StateFlow<Boolean>
}
