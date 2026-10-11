package com.speakmind.app.feature.voice.platform

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

actual class VoicePlayer actual constructor() : KoinComponent {

    private val context: Context by inject()
    private var player: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    actual val progress: StateFlow<Float> = _progress.asStateFlow()

    actual suspend fun playFromBytes(audioData: ByteArray) {
        stop()
        val tempFile = File(context.cacheDir, "playback_${System.currentTimeMillis()}.m4a")
        tempFile.writeBytes(audioData)
        playFile(tempFile.absolutePath)
    }

    actual suspend fun playFromUrl(url: String) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(url)
            setOnCompletionListener {
                _isPlaying.value = false
                _progress.value = 0f
            }
            prepareAsync()
            setOnPreparedListener {
                start()
                _isPlaying.value = true
            }
        }
    }

    private fun playFile(path: String) {
        player = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener {
                _isPlaying.value = false
                _progress.value = 0f
            }
            prepare()
            start()
            _isPlaying.value = true
        }
    }

    actual fun stop() {
        player?.stop()
        player?.release()
        player = null
        _isPlaying.value = false
        _progress.value = 0f
    }

    actual fun release() {
        stop()
    }
}
