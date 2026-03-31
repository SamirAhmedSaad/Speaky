package com.speakmind.app.feature.voice.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.setActive
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognitionTask
import platform.Foundation.NSLocale

actual class SpeechRecognizerEngine actual constructor() {

    private var speechRecognizer: SFSpeechRecognizer? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var audioEngine: AVAudioEngine? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null

    private val _results = MutableStateFlow<SpeechResult>(SpeechResult.Idle)
    actual val results: StateFlow<SpeechResult> = _results.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    actual val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    actual fun isAvailable(): Boolean {
        return SFSpeechRecognizer()?.isAvailable() ?: false
    }

    actual fun startListening(language: String) {
        if (_isListening.value) return

        try {
            speechRecognizer = SFSpeechRecognizer(locale = NSLocale(language))
            audioEngine = AVAudioEngine()
            recognitionRequest = SFSpeechAudioBufferRecognitionRequest().apply {
                shouldReportPartialResults = true
            }

            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryRecord, null)
            audioSession.setActive(true, null)

            val inputNode = audioEngine!!.inputNode
            val request = recognitionRequest!!

            recognitionTask = speechRecognizer?.recognitionTaskWithRequest(request) { result, error ->
                if (error != null) {
                    _results.value = SpeechResult.Error(error.localizedDescription)
                    stopListening()
                    return@recognitionTaskWithRequest
                }
                result?.let { res ->
                    val transcript = res.bestTranscription.formattedString
                    if (res.isFinal()) {
                        _results.value = SpeechResult.Final(transcript)
                        stopListening()
                    } else {
                        _results.value = SpeechResult.Partial(transcript)
                    }
                }
            }

            val format = inputNode.outputFormatForBus(0u)
            inputNode.installTapOnBus(0u, bufferSize = 1024u, format = format) { buffer, _ ->
                buffer?.let { recognitionRequest?.appendAudioPCMBuffer(it) }
            }

            audioEngine?.prepare()
            audioEngine?.startAndReturnError(null)
            _isListening.value = true

        } catch (e: Exception) {
            _results.value = SpeechResult.Error(e.message ?: "Failed to start recognition")
            _isListening.value = false
        }
    }

    actual fun stopListening() {
        audioEngine?.stop()
        audioEngine?.inputNode?.removeTapOnBus(0u)
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        _isListening.value = false

        recognitionTask = null
        recognitionRequest = null
        audioEngine = null
    }
}
