package com.speakmind.app.feature.voice.platform

import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTTS
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale
import kotlin.coroutines.resume

actual class TextToSpeechEngine actual constructor() : KoinComponent {

    private val context: Context by inject()
    private var tts: AndroidTTS? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    actual val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private fun ensureInitialized(onReady: () -> Unit) {
        if (isInitialized && tts != null) {
            onReady()
            return
        }
        tts = AndroidTTS(context) { status ->
            if (status == AndroidTTS.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.US
                onReady()
            }
        }
    }

    actual fun isAvailable(): Boolean = isInitialized

    actual suspend fun speak(text: String, rate: Float, language: String) {
        suspendCancellableCoroutine { cont ->
            ensureInitialized {
                tts?.setSpeechRate(rate)
                tts?.language = Locale.forLanguageTag(language)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                })

                tts?.speak(text, AndroidTTS.QUEUE_FLUSH, null, "speak_${System.currentTimeMillis()}")
            }

            cont.invokeOnCancellation {
                tts?.stop()
                _isSpeaking.value = false
            }
        }
    }

    actual fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }
}
