package com.speakmind.app.core.network

interface StorageService {
    suspend fun uploadVoiceRecording(
        userId: String,
        conversationId: String,
        messageId: String,
        audioData: ByteArray,
    ): String

    suspend fun downloadVoiceRecording(url: String): ByteArray

    suspend fun deleteVoiceRecording(url: String)
}
