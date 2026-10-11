package com.speakmind.app.core.network

import com.speakmind.app.core.domain.model.AuthResult
import com.speakmind.app.core.domain.model.AuthState
import com.speakmind.app.core.domain.model.Conversation
import com.speakmind.app.core.domain.model.User
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface BackendService {
    suspend fun signUp(email: String, password: String, displayName: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun signOut()
    suspend fun getCurrentUser(): User?
    fun observeAuthState(): Flow<AuthState>

    suspend fun createConversation(title: String, scenarioId: String?): Conversation
    suspend fun getConversations(): List<Conversation>
    suspend fun deleteConversation(conversationId: String)

    suspend fun getMessages(conversationId: String): List<ChatMessage>
    suspend fun saveMessage(conversationId: String, message: ChatMessage)

    suspend fun updateProfile(user: User)
}
