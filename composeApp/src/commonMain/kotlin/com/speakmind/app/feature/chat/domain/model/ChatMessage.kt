package com.speakmind.app.feature.chat.domain.model

import kotlinx.serialization.Serializable

enum class MessageRole { USER, ASSISTANT, SYSTEM }

enum class CorrectionType { GRAMMAR, VOCABULARY, PRONUNCIATION, WORD_ORDER }

@Serializable
data class Correction(
    val original: String,
    val corrected: String,
    val type: String,
    val explanation: String,
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val corrections: List<Correction> = emptyList(),
    val timestamp: Long = 0L,
    val isStreaming: Boolean = false,
)
