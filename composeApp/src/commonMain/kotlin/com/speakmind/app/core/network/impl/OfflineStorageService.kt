package com.speakmind.app.core.network.impl

import com.speakmind.app.core.network.StorageService

class OfflineStorageService : StorageService {

    override suspend fun uploadVoiceRecording(
        userId: String,
        conversationId: String,
        messageId: String,
        audioData: ByteArray,
    ): String = ""

    override suspend fun downloadVoiceRecording(url: String): ByteArray = ByteArray(0)

    override suspend fun deleteVoiceRecording(url: String) {}
}
