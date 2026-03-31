package com.speakmind.app.feature.ai.platform

import android.os.Environment
import android.os.StatFs
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Android model file manager.
 * Stores the GGUF model in app-specific files directory.
 */
actual class ModelFileManager actual constructor() : KoinComponent {

    private val context: android.content.Context by inject()

    private val modelFileName = "llama-3.2-3b-q4_k_m.gguf"

    actual fun getModelDirectory(): String {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    actual fun modelExists(): Boolean {
        return File(getModelDirectory(), modelFileName).exists()
    }

    actual fun getModelPath(): String {
        return File(getModelDirectory(), modelFileName).absolutePath
    }

    actual suspend fun downloadModel(url: String, onProgress: (Float) -> Unit): Boolean {
        // TODO: Implement model download via HttpURLConnection with progress
        // For now, mark as available (skip download for development)
        return true
    }

    actual fun deleteModel() {
        File(getModelPath()).delete()
    }

    actual fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }
}
