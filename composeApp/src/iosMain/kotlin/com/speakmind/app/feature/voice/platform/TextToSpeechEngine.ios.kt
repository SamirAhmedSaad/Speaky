package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual class TextToSpeechEngine actual constructor() {

    private val synthesizer = AVSpeechSynthesizer()

    private val _isSpeaking = MutableStateFlow(false)
    actual val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    actual fun isAvailable(): Boolean = true

    actual suspend fun speak(text: String, rate: Float, language: String) {
        suspendCancellableCoroutine { cont ->
            val utterance = AVSpeechUtterance.speechUtteranceWithString(text).apply {
                this.rate = rate * 0.5f // AVSpeech rate is 0-1 where 0.5 is normal
                this.voice = platform.AVFAudio.AVSpeechSynthesisVoice.voiceWithLanguage(language)
            }

            val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
                override fun speechSynthesizer(
                    synthesizer: AVSpeechSynthesizer,
                    didFinishSpeechUtterance: AVSpeechUtterance
                ) {
                    _isSpeaking.value = false
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun speechSynthesizer(
                    synthesizer: AVSpeechSynthesizer,
                    didStartSpeechUtterance: AVSpeechUtterance
                ) {
                    _isSpeaking.value = true
                }
            }

            synthesizer.delegate = delegate
            synthesizer.speakUtterance(utterance)

            cont.invokeOnCancellation {
                synthesizer.stopSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundaryImmediate)
                _isSpeaking.value = false
            }
        }
    }

    actual fun stop() {
        synthesizer.stopSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundaryImmediate)
        _isSpeaking.value = false
    }
}
