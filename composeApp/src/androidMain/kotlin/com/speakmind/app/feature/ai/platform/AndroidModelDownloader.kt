package com.speakmind.app.feature.ai.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class AndroidModelDownloader(private val context: Context) : ModelDownloader {

    private val workManager = WorkManager.getInstance(context)
    private val modelsDir = File(context.filesDir, "models")

    companion object {
        const val WORK_NAME = "model_download"
        const val KEY_MODEL_URL = "model_url"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_STATUS = "status"
        const val KEY_OUTPUT_PATH = "output_path"
    }

    override fun modelExists(): Boolean {
        val modelFile = File(modelsDir, "model.gguf")
        if (modelFile.exists() && modelFile.length() > 100_000_000) return true
        // Also check Downloads folder
        return findModelFile() != null
    }

    override fun getModelPath(): String? {
        val appModel = File(modelsDir, "model.gguf")
        if (appModel.exists() && appModel.length() > 100_000_000) return appModel.absolutePath
        return findModelFile()
    }

    override fun startDownload(url: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only
            .setRequiresStorageNotLow(true)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<com.speakmind.app.feature.ai.platform.download.ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_MODEL_URL to url))
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            downloadRequest,
        )
    }

    override fun cancelDownload() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    override fun observeDownload(): Flow<ModelDownloadState> {
        return workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .map { workInfos ->
                val info = workInfos.firstOrNull()
                if (info == null) {
                    ModelDownloadState()
                } else {
                    val progress = info.progress
                    val pct = progress.getInt(KEY_PROGRESS, 0)
                    val downloaded = progress.getLong(KEY_DOWNLOADED_BYTES, 0)
                    val total = progress.getLong(KEY_TOTAL_BYTES, 0)

                    when (info.state) {
                        WorkInfo.State.RUNNING -> ModelDownloadState(
                            isDownloading = true,
                            progress = pct,
                            downloadedMB = downloaded / (1024 * 1024),
                            totalMB = total / (1024 * 1024),
                        )
                        WorkInfo.State.SUCCEEDED -> {
                            val path = info.outputData.getString(KEY_OUTPUT_PATH)
                            ModelDownloadState(isComplete = true, progress = 100, modelPath = path)
                        }
                        WorkInfo.State.FAILED -> ModelDownloadState(isError = true)
                        WorkInfo.State.ENQUEUED -> ModelDownloadState(isDownloading = true, progress = 0)
                        else -> ModelDownloadState()
                    }
                }
            }
    }
}
