package com.speakmind.app.feature.voice.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class SpeechRecognizerEngine actual constructor() : KoinComponent {

    private val context: Context by inject()
    private val micPermissionRequester: MicPermissionRequester by inject()
    private var recognizer: AndroidSpeechRecognizer? = null

    private val _results = MutableStateFlow<SpeechResult>(SpeechResult.Idle)
    actual val results: StateFlow<SpeechResult> = _results.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    actual val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    actual fun isAvailable(): Boolean {
        return AndroidSpeechRecognizer.isRecognitionAvailable(context)
    }

    actual fun startListening(language: String) {
        if (_isListening.value) return
        // Mark eagerly to block any concurrent start call
        _isListening.value = true

        if (!micPermissionRequester.requestIfNeeded(context)) {
            _isListening.value = false
            _results.value = SpeechResult.Error("Microphone permission required")
            return
        }

        // Null out before destroy so any in-flight callback skips emitting on a dead recognizer
        val old = recognizer
        recognizer = null
        old?.destroy()

        recognizer = AndroidSpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _results.value = SpeechResult.Idle
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val msg = when (error) {
                        AndroidSpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        AndroidSpeechRecognizer.ERROR_NETWORK -> "Network error"
                        AndroidSpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Recognition error ($error)"
                    }
                    _results.value = SpeechResult.Error(msg)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results?.getFloatArray(AndroidSpeechRecognizer.CONFIDENCE_SCORES)
                    if (!matches.isNullOrEmpty()) {
                        _results.value = SpeechResult.Final(
                            text = matches[0],
                            confidence = scores?.firstOrNull() ?: 1f
                        )
                    } else {
                        // Empty result — treat like no-match so the ViewModel can react
                        _results.value = SpeechResult.Error("No speech recognized")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _results.value = SpeechResult.Partial(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Extend silence thresholds so recording doesn't cut off during natural pauses
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
        }

        recognizer?.startListening(intent)
    }

    actual fun stopListening() {
        // Use stopListening (not cancel/destroy) so Android still fires onResults with what it heard
        recognizer?.stopListening()
        _isListening.value = false
    }
}
