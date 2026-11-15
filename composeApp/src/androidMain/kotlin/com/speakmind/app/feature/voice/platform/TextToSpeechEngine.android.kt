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
import java.util.concurrent.Executors
import kotlin.coroutines.resume

actual class TextToSpeechEngine actual constructor() : KoinComponent {

    private val context: Context by inject()
    private var tts: AndroidTTS? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    actual val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Every call into Android's TextToSpeech can block on a binder call to the TTS service (for example
    // while it is still connecting), which froze the main thread long enough to cause ANRs. All TTS work
    // runs on this one background thread instead, in order.
    private val ttsThread = Executors.newSingleThreadExecutor { r -> Thread(r, "speaky-tts").apply { isDaemon = true } }

    private fun onTtsThread(block: () -> Unit) {
        ttsThread.execute {
            try {
                block()
            } catch (e: Exception) {
                io.github.aakira.napier.Napier.w("TTS call failed", e)
            }
        }
    }

    private fun ensureInitialized(onReady: () -> Unit) {
        if (isInitialized && tts != null) {
            onTtsThread(onReady)
            return
        }
        tts = AndroidTTS(context) { status ->
            if (status == AndroidTTS.SUCCESS) {
                isInitialized = true
                onTtsThread {
                    tts?.language = Locale.US
                    onReady()
                }
            }
        }
    }

    actual fun isAvailable(): Boolean = isInitialized

    // Android TTS has a hard character limit; split long text into sentence-boundary chunks.
    actual suspend fun speak(text: String, rate: Float, language: String) {
        val maxLen = AndroidTTS.getMaxSpeechInputLength()
        val chunks = chunkBySentence(text, maxLen)
        for (chunk in chunks) {
            speakChunk(chunk, rate, language)
        }
    }

    private suspend fun speakChunk(text: String, rate: Float, language: String) {
        suspendCancellableCoroutine { cont ->
            ensureInitialized {
                if (!cont.isActive) return@ensureInitialized // cancelled while TTS was starting up
                tts?.stop()
                tts?.setSpeechRate(rate)
                tts?.language = Locale.forLanguageTag(language)

                val id = "speak_${System.nanoTime()}"

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == id) _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id) {
                            _isSpeaking.value = false
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id) {
                            _isSpeaking.value = false
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                })

                tts?.speak(text, AndroidTTS.QUEUE_FLUSH, null, id)
            }

            cont.invokeOnCancellation {
                _isSpeaking.value = false
                onTtsThread { tts?.stop() }
            }
        }
    }

    actual fun stop() {
        _isSpeaking.value = false
        onTtsThread { tts?.stop() }
    }

    private fun chunkBySentence(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val chunks = mutableListOf<String>()
        val buf = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
            if (buf.length + sentence.length + 1 > maxLen) {
                if (buf.isNotEmpty()) { chunks.add(buf.toString().trim()); buf.clear() }
                if (sentence.length > maxLen) {
                    var i = 0
                    while (i < sentence.length) { chunks.add(sentence.substring(i, minOf(i + maxLen, sentence.length))); i += maxLen }
                } else buf.append(sentence).append(' ')
            } else buf.append(sentence).append(' ')
        }
        if (buf.isNotEmpty()) chunks.add(buf.toString().trim())
        return chunks
    }
}
