package com.speakmind.app.feature.community.data.model

data class ChannelMessage(
    val id: String,
    val senderId: String,
    val senderNickname: String,
    val senderPhotoUrl: String,
    val senderGender: String,
    val text: String,
    val timestamp: Long,
    val isSynced: Boolean = false,
)
