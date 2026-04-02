package com.speakmind.app.feature.ai.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val isWaitingForWifi: Boolean = false,
    val progress: Int = 0,
    val downloadedMB: Long = 0,
    val totalMB: Long = 0,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val modelPath: String? = null,
)

interface ModelDownloader {
    fun modelExists(): Boolean
    fun getModelPath(): String?
    fun startDownload(url: String = DEFAULT_MODEL_URL)
    fun cancelDownload()
    fun observeDownload(): Flow<ModelDownloadState>

    companion object {
        // Qwen2.5 1.5B Instruct Q4_K_M (~1 GB) — non-gated, strong instruction-following for English tutoring
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
    }
}

/** No-op implementation for platforms without WorkManager */
class NoOpModelDownloader : ModelDownloader {
    override fun modelExists(): Boolean = false
    override fun getModelPath(): String? = null
    override fun startDownload(url: String) {}
    override fun cancelDownload() {}
    override fun observeDownload(): Flow<ModelDownloadState> = flowOf(ModelDownloadState())
}
