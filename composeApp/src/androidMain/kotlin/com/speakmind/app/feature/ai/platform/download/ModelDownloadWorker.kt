package com.speakmind.app.feature.ai.platform.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_URL = "model_url"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_STATUS = "status"
        const val WORK_NAME = "model_download"

        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"

        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_MODEL_URL) ?: DEFAULT_MODEL_URL
        val outputDir = File(applicationContext.filesDir, "models")
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, "model.gguf")
        val tempFile = File(outputDir, "model.gguf.tmp")

        // Check if already downloaded
        if (outputFile.exists() && outputFile.length() > 100_000_000) {
            setProgressAsync(workDataOf(
                KEY_PROGRESS to 100,
                KEY_STATUS to "complete",
                KEY_OUTPUT_PATH to outputFile.absolutePath,
            ))
            return Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))
        }

        // Start as foreground with notification so Android doesn't kill us
        setForeground(createForegroundInfo(0))

        return try {
            var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                Napier.d { "Resuming download from byte $downloadedBytes" }
            }

            connection.connect()

            val responseCode = connection.responseCode
            val totalBytes = if (responseCode == 206) {
                val contentRange = connection.getHeaderField("Content-Range")
                contentRange?.substringAfter("/")?.toLongOrNull()
                    ?: (connection.contentLengthLong + downloadedBytes)
            } else {
                downloadedBytes = 0
                tempFile.delete()
                connection.contentLengthLong
            }

            Napier.d { "Download: total=$totalBytes, starting from=$downloadedBytes" }

            setProgressAsync(workDataOf(
                KEY_PROGRESS to ((downloadedBytes * 100) / totalBytes).toInt(),
                KEY_DOWNLOADED_BYTES to downloadedBytes,
                KEY_TOTAL_BYTES to totalBytes,
                KEY_STATUS to "downloading",
            ))

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile, downloadedBytes > 0)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastProgressUpdate = System.currentTimeMillis()

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            Napier.d { "Download stopped by user" }
                            return Result.failure()
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 500) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            setProgressAsync(workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_DOWNLOADED_BYTES to downloadedBytes,
                                KEY_TOTAL_BYTES to totalBytes,
                                KEY_STATUS to "downloading",
                            ))
                            // Update notification
                            setForeground(createForegroundInfo(progress))
                            lastProgressUpdate = now
                        }
                    }
                }
            }

            connection.disconnect()
            tempFile.renameTo(outputFile)

            Napier.d { "Download complete: ${outputFile.absolutePath}" }

            setProgressAsync(workDataOf(
                KEY_PROGRESS to 100,
                KEY_STATUS to "complete",
                KEY_OUTPUT_PATH to outputFile.absolutePath,
            ))

            Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))

        } catch (e: Exception) {
            Napier.e { "Download failed: ${e.message}" }
            setProgressAsync(workDataOf(KEY_STATUS to "error"))
            Result.retry()
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading AI Model")
            .setContentText(if (progress > 0) "$progress% complete" else "Starting download...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while downloading the AI model"
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
