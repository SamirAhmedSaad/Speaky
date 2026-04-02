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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
        const val KEY_ERROR_MESSAGE = "error_message"
        const val WORK_NAME = "model_download"

        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"

        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_REDIRECTS = 10
        private const val BUFFER_SIZE = 32 * 1024 // 32 KB
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_MODEL_URL) ?: DEFAULT_MODEL_URL
        val outputDir = File(applicationContext.filesDir, "models")
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, "model.gguf")
        val tempFile = File(outputDir, "model.gguf.tmp")

        // Already downloaded
        if (outputFile.exists() && outputFile.length() > 100_000_000) {
            setProgressAsync(workDataOf(
                KEY_PROGRESS to 100,
                KEY_STATUS to "complete",
                KEY_OUTPUT_PATH to outputFile.absolutePath,
            ))
            return Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))
        }

        setForeground(createForegroundInfo(0))

        return try {
            var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
            Napier.d { "Starting download. Resuming from byte $downloadedBytes" }

            val connection = openConnectionFollowingRedirects(url, downloadedBytes)

            val responseCode = connection.responseCode
            Napier.d { "Response code: $responseCode" }

            // Non-recoverable HTTP errors — fail permanently (no retry)
            if (responseCode in listOf(401, 403, 404, 410)) {
                val msg = "Server returned $responseCode — cannot download model"
                Napier.e { msg }
                connection.disconnect()
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
            }

            val totalBytes: Long
            when (responseCode) {
                206 -> {
                    // Partial content — resume is working
                    val contentRange = connection.getHeaderField("Content-Range")
                    totalBytes = contentRange?.substringAfter("/")?.toLongOrNull()
                        ?: (connection.contentLengthLong.takeIf { it >= 0 }?.plus(downloadedBytes) ?: -1L)
                }
                200 -> {
                    // Server ignored Range header — restart from scratch
                    if (downloadedBytes > 0) {
                        Napier.d { "Server doesn't support resume, restarting download" }
                        downloadedBytes = 0
                        tempFile.delete()
                    }
                    totalBytes = connection.contentLengthLong
                }
                else -> {
                    val msg = "Unexpected response: $responseCode"
                    Napier.e { msg }
                    connection.disconnect()
                    return Result.retry()
                }
            }

            Napier.d { "Download: total=${totalBytes}B, resuming from ${downloadedBytes}B" }

            setProgressAsync(workDataOf(
                KEY_PROGRESS to if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0,
                KEY_DOWNLOADED_BYTES to downloadedBytes,
                KEY_TOTAL_BYTES to totalBytes,
                KEY_STATUS to "downloading",
            ))

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile, downloadedBytes > 0)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var lastProgressUpdate = System.currentTimeMillis()

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Suspension point — lets WorkManager cancel the coroutine cleanly
                        // (e.g. WiFi dropped, constraint no longer met)
                        yield()

                        if (isStopped) {
                            // Constraint lost or explicit cancel — retry so WorkManager
                            // re-queues when WiFi comes back. cancelUniqueWork() will
                            // keep the state CANCELLED regardless of this result.
                            Napier.d { "Worker stopped — retrying when constraints met again" }
                            return Result.retry()
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 500) {
                            val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                            setProgressAsync(workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_DOWNLOADED_BYTES to downloadedBytes,
                                KEY_TOTAL_BYTES to totalBytes,
                                KEY_STATUS to "downloading",
                            ))
                            setForeground(createForegroundInfo(progress))
                            lastProgressUpdate = now
                        }
                    }
                }
            }

            connection.disconnect()

            // Verify download is complete when we know the expected size
            if (totalBytes > 0 && tempFile.length() < totalBytes) {
                Napier.e { "Download incomplete: got ${tempFile.length()} of $totalBytes bytes — retrying" }
                return Result.retry()
            }

            // Reject tiny files that are likely error pages
            if (tempFile.length() < 1_000_000) {
                Napier.e { "Downloaded file suspiciously small (${tempFile.length()} bytes) — retrying" }
                tempFile.delete()
                return Result.retry()
            }

            tempFile.renameTo(outputFile)
            Napier.d { "Download complete: ${outputFile.absolutePath}, size=${outputFile.length()}" }

            setProgressAsync(workDataOf(
                KEY_PROGRESS to 100,
                KEY_STATUS to "complete",
                KEY_OUTPUT_PATH to outputFile.absolutePath,
            ))

            Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))

        } catch (e: CancellationException) {
            // Coroutine cancelled by WorkManager (e.g. WiFi constraint lost).
            // Re-throw so WorkManager re-enqueues the work and retries immediately
            // when constraints are satisfied again — without exponential backoff.
            throw e
        } catch (e: Exception) {
            Napier.e { "Download failed: ${e.message}" }
            setProgressAsync(workDataOf(KEY_STATUS to "error"))
            Result.retry()
        }
    }

    /**
     * Opens an HTTP connection following redirects manually so the Range header
     * is preserved on every hop. Java's HttpURLConnection auto-follow drops Range
     * on redirect, which silently breaks resume downloads.
     */
    private fun openConnectionFollowingRedirects(startUrl: String, rangeStart: Long): HttpURLConnection {
        var currentUrl = startUrl
        var hops = 0

        while (hops < MAX_REDIRECTS) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false // we handle redirects manually
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (rangeStart > 0) {
                conn.setRequestProperty("Range", "bytes=$rangeStart-")
            }

            conn.connect()

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw IOException("HTTP $code redirect with no Location header")
                Napier.d { "Redirect $hops: $code → $location" }
                conn.disconnect()
                currentUrl = location
                hops++
            } else {
                return conn
            }
        }

        throw IOException("Too many redirects (> $MAX_REDIRECTS)")
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
