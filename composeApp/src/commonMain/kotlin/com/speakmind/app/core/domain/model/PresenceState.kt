package com.speakmind.app.core.domain.model

data class PresenceState(
    val userId: String,
    val isOnline: Boolean = false,
    val isTyping: Boolean = false,
    val lastSeen: Long = 0L,
)
