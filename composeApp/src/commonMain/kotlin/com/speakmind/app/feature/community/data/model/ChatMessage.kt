package com.speakmind.app.feature.community.data.model

data class ChatMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isSynced: Boolean = false,
)
