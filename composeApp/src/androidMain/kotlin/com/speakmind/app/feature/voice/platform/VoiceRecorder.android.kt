package com.speakmind.app.feature.voice.platform

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

actual class VoiceRecorder actual constructor() : KoinComponent {

    private val context: Context by inject()
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitudes = MutableStateFlow(0f)
    actual val amplitudes: StateFlow<Float> = _amplitudes.asStateFlow()

    actual fun startRecording() {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        _isRecording.value = true
    }

    actual fun stopRecording(): ByteArray {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            _isRecording.value = false
            _amplitudes.value = 0f
            outputFile?.readBytes() ?: ByteArray(0)
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            _isRecording.value = false
            ByteArray(0)
        }
    }

    actual fun cancelRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
        _isRecording.value = false
        _amplitudes.value = 0f
    }
}
