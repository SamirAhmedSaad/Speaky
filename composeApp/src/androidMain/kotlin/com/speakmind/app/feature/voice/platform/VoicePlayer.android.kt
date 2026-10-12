package com.speakmind.app.feature.voice.platform

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

actual class VoicePlayer actual constructor() : KoinComponent {

    private val context: Context by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var tempFile: File? = null
    private var ticker: Job? = null
    private var speed = 1f

    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    actual val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    actual val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    actual val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    actual suspend fun playFromBytes(audioData: ByteArray) {
        stop()
        val file = File(context.cacheDir, "playback_${System.currentTimeMillis()}.m4a")
        file.writeBytes(audioData)
        tempFile = file
        start { setDataSource(file.absolutePath) }
    }

    actual suspend fun playFromUrl(url: String) {
        stop()
        start { setDataSource(url) }
    }

    private fun start(source: MediaPlayer.() -> Unit) {
        player = MediaPlayer().apply {
            source()
            setOnCompletionListener {
                stopTicker()
                _progress.value = 1f
                _isPlaying.value = false
                _isPaused.value = false
                _progress.value = 0f
                _positionMs.value = 0L
            }
            setOnPreparedListener { mp ->
                applySpeed(mp)
                mp.start()
                _isPlaying.value = true
                _isPaused.value = false
                startTicker()
            }
            prepareAsync()
        }
    }

    actual fun pause() {
        val p = player ?: return
        if (!p.isPlaying) return
        p.pause()
        stopTicker()
        publishPosition(p)
        _isPlaying.value = false
        _isPaused.value = true
    }

    actual fun resume() {
        val p = player ?: return
        if (!_isPaused.value) return
        applySpeed(p)
        p.start()
        _isPlaying.value = true
        _isPaused.value = false
        startTicker()
    }

    actual fun seekTo(fraction: Float) {
        val p = player ?: return
        val duration = runCatching { p.duration }.getOrDefault(0)
        if (duration <= 0) return
        p.seekTo((fraction.coerceIn(0f, 1f) * duration).toInt())
        publishPosition(p, overrideMs = (fraction.coerceIn(0f, 1f) * duration).toLong())
    }

    actual fun setSpeed(speed: Float) {
        this.speed = speed
        // Changing PlaybackParams on a paused MediaPlayer starts it, so wait for resume() then.
        player?.takeIf { it.isPlaying }?.let { applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive) {
                player?.let { publishPosition(it) }
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun publishPosition(p: MediaPlayer, overrideMs: Long? = null) {
        val duration = runCatching { p.duration }.getOrDefault(0)
        val position = overrideMs ?: runCatching { p.currentPosition.toLong() }.getOrDefault(0L)
        _positionMs.value = position
        _progress.value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    actual fun stop() {
        stopTicker()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        tempFile?.delete()
        tempFile = null
        _isPlaying.value = false
        _isPaused.value = false
        _progress.value = 0f
        _positionMs.value = 0L
    }

    actual fun release() {
        stop()
    }

    private companion object {
        /** ~30 fps keeps the waveform fill smooth. */
        const val TICK_MS = 33L
    }
}
