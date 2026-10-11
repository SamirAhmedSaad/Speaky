package com.speakmind.app.core.network.impl

import com.speakmind.app.core.domain.model.AuthResult
import com.speakmind.app.core.domain.model.AuthState
import com.speakmind.app.core.domain.model.Conversation
import com.speakmind.app.core.domain.model.User
import com.speakmind.app.core.network.BackendService
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class OfflineBackendService : BackendService {

    private val offlineUser = User(
        id = "offline",
        email = "",
        displayName = "Guest",
    )

    private val authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

    override suspend fun signUp(email: String, password: String, displayName: String): AuthResult {
        return AuthResult.Error("Offline mode — sign up not available")
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        return AuthResult.Error("Offline mode — sign in not available")
    }

    override suspend fun signOut() {}

    override suspend fun getCurrentUser(): User = offlineUser

    override fun observeAuthState(): Flow<AuthState> = authState

    private var nextId = 1L

    override suspend fun createConversation(title: String, scenarioId: String?): Conversation {
        return Conversation(
            id = "offline_${nextId++}",
            title = title,
            scenarioId = scenarioId,
        )
    }

    override suspend fun getConversations(): List<Conversation> = emptyList()

    override suspend fun deleteConversation(conversationId: String) {}

    override suspend fun getMessages(conversationId: String): List<ChatMessage> = emptyList()

    override suspend fun saveMessage(conversationId: String, message: ChatMessage) {}

    override suspend fun updateProfile(user: User) {}
}
