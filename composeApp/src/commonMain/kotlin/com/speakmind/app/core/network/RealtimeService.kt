package com.speakmind.app.core.network

import com.speakmind.app.core.domain.model.PresenceState
import kotlinx.coroutines.flow.Flow

interface RealtimeService {
    suspend fun joinConversation(conversationId: String)
    suspend fun leaveConversation()
    suspend fun sendTypingIndicator(conversationId: String, isTyping: Boolean)
    fun observeTypingState(conversationId: String): Flow<Boolean>
    suspend fun setOnline()
    suspend fun setOffline()
    fun observePresence(): Flow<PresenceState>
}
