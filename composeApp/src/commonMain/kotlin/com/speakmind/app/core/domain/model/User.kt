package com.speakmind.app.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    val level: String = "A2",
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("total_vocab_learned") val totalVocabLearned: Int = 0,
    @SerialName("total_conversations") val totalConversations: Int = 0,
    @SerialName("total_minutes") val totalMinutes: Int = 0,
)
