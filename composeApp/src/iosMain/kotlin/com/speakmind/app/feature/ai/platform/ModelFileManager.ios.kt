package com.speakmind.app.feature.ai.platform

import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

/**
 * iOS model file manager.
 * Stores the GGUF model in the app's Documents directory.
 */
actual class ModelFileManager actual constructor() {

    private val modelFileName = "llama-3.2-3b-q4_k_m.gguf"

    actual fun getModelDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDir = paths.firstOrNull() as? String ?: ""
        val modelDir = "$documentsDir/models"
        NSFileManager.defaultManager.createDirectoryAtPath(
            modelDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return modelDir
    }

    actual fun modelExists(): Boolean {
        return NSFileManager.defaultManager.fileExistsAtPath(getModelPath())
    }

    actual fun getModelPath(): String {
        return "${getModelDirectory()}/$modelFileName"
    }

    actual suspend fun downloadModel(url: String, onProgress: (Float) -> Unit): Boolean {
        // TODO: Implement model download via NSURLSession with progress
        return true
    }

    actual fun deleteModel() {
        NSFileManager.defaultManager.removeItemAtPath(getModelPath(), null)
    }

    actual fun getAvailableStorageBytes(): Long {
        return try {
            val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(
                getModelDirectory(),
                error = null
            )
            (attrs?.get("NSFileSystemFreeSize") as? Number)?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
