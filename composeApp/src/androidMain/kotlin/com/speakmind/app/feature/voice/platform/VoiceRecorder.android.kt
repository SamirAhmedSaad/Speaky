package com.speakmind.app.feature.voice.platform

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import kotlin.math.log10

actual class VoiceRecorder actual constructor() : KoinComponent {

    private val context: Context by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var meter: Job? = null
    private val levels = mutableListOf<Float>()

    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitudes = MutableStateFlow(0f)
    actual val amplitudes: StateFlow<Float> = _amplitudes.asStateFlow()

    actual fun startRecording() {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        synchronized(levels) { levels.clear() }

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // Speech-quality mono: ~4 KB/s instead of ~16 KB/s, so clips upload and load fast.
            setAudioChannels(1)
            setAudioSamplingRate(22_050)
            setAudioEncodingBitRate(32_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        _isRecording.value = true
        startMeter()
    }

    private fun startMeter() {
        meter?.cancel()
        meter = scope.launch {
            while (isActive) {
                val raw = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                val level = toLevel(raw)
                _amplitudes.value = level
                synchronized(levels) { levels += level }
                delay(METER_MS)
            }
        }
    }

    /** maxAmplitude (0..32767) → 0..1 on a ~45 dB scale, which matches how loud speech feels. */
    private fun toLevel(amplitude: Int): Float {
        if (amplitude <= 0) return 0f
        val db = 20 * log10(amplitude / 32767.0)
        return ((db + 45) / 45).toFloat().coerceIn(0f, 1f)
    }

    actual fun recordedLevels(bars: Int): List<Float> {
        val samples = synchronized(levels) { levels.toList() }
        if (samples.isEmpty() || bars <= 0) return emptyList()
        val buckets = (0 until bars).map { i ->
            val from = i * samples.size / bars
            val to = maxOf(from + 1, (i + 1) * samples.size / bars).coerceAtMost(samples.size)
            samples.subList(from.coerceAtMost(samples.size - 1), to).maxOrNull() ?: 0f
        }
        // Scale to the loudest bar so quiet speakers still get a readable shape.
        val peak = buckets.maxOrNull()?.takeIf { it > 0f } ?: return List(bars) { MIN_BAR }
        return buckets.map { (it / peak).coerceIn(MIN_BAR, 1f) }
    }

    private fun stopMeter() {
        meter?.cancel()
        meter = null
        _amplitudes.value = 0f
    }

    actual fun stopRecording(): ByteArray {
        stopMeter()
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            _isRecording.value = false
            outputFile?.let { file -> file.readBytes().also { file.delete() } } ?: ByteArray(0)
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            _isRecording.value = false
            outputFile?.delete()
            ByteArray(0)
        } finally {
            outputFile = null
        }
    }

    actual fun cancelRecording() {
        stopMeter()
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
        _isRecording.value = false
    }

    private companion object {
        const val METER_MS = 60L
        const val MIN_BAR = 0.12f
    }
}
