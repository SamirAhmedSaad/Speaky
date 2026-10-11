package com.speakmind.app.core.network.impl

import com.speakmind.app.core.network.StorageService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage

class SupabaseStorageService(
    private val client: SupabaseClient,
) : StorageService {

    private val bucket get() = client.storage.from("voice-recordings")

    override suspend fun uploadVoiceRecording(
        userId: String,
        conversationId: String,
        messageId: String,
        audioData: ByteArray,
    ): String {
        val path = "$userId/$conversationId/$messageId.m4a"
        bucket.upload(path, audioData) { upsert = true }
        return bucket.publicUrl(path)
    }

    override suspend fun downloadVoiceRecording(url: String): ByteArray {
        return bucket.downloadAuthenticated(url)
    }

    override suspend fun deleteVoiceRecording(url: String) {
        bucket.delete(url)
    }
}
