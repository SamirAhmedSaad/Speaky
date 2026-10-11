package com.speakmind.app.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    val title: String,
    @SerialName("scenario_id") val scenarioId: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String = "",
    @SerialName("last_message_at") val lastMessageAt: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("message_count") val messageCount: Int = 0,
)
