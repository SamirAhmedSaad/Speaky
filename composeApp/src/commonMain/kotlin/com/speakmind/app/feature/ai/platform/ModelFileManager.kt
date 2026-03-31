package com.speakmind.app.feature.ai.platform

/**
 * Platform-specific model file management.
 * Handles model storage path, existence check, and download.
 */
expect class ModelFileManager() {
    fun getModelDirectory(): String
    fun modelExists(): Boolean
    fun getModelPath(): String
    suspend fun downloadModel(url: String, onProgress: (Float) -> Unit): Boolean
    fun deleteModel()
    fun getAvailableStorageBytes(): Long
}
