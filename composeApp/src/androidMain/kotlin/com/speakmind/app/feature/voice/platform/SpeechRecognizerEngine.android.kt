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

        recognizer?.destroy()
        recognizer = AndroidSpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
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
                    val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results?.getFloatArray(AndroidSpeechRecognizer.CONFIDENCE_SCORES)
                    if (!matches.isNullOrEmpty()) {
                        _results.value = SpeechResult.Final(
                            text = matches[0],
                            confidence = scores?.firstOrNull() ?: 1f
                        )
                    }
                    _isListening.value = false
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
        }

        recognizer?.startListening(intent)
    }

    actual fun stopListening() {
        recognizer?.stopListening()
        _isListening.value = false
    }
}
