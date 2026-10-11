package com.speakmind.app.core.network.impl

import com.speakmind.app.core.domain.model.AuthResult
import com.speakmind.app.core.domain.model.AuthState
import com.speakmind.app.core.domain.model.Conversation
import com.speakmind.app.core.domain.model.User
import com.speakmind.app.core.network.BackendService
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val level: String = "A2",
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("total_vocab_learned") val totalVocabLearned: Int = 0,
    @SerialName("total_conversations") val totalConversations: Int = 0,
    @SerialName("total_minutes") val totalMinutes: Int = 0,
)

@Serializable
private data class MessageRow(
    val id: String? = null,
    @SerialName("conversation_id") val conversationId: String,
    val role: String,
    val content: String,
    @SerialName("corrections_json") val correctionsJson: String? = null,
    @SerialName("voice_url") val voiceUrl: String? = null,
    @SerialName("voice_duration_ms") val voiceDurationMs: Int = 0,
)

@Serializable
private data class InsertProfile(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
private data class InsertConversation(
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("scenario_id") val scenarioId: String? = null,
)

class SupabaseBackendService(
    private val client: SupabaseClient,
) : BackendService {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)

    override suspend fun signUp(email: String, password: String, displayName: String): AuthResult {
        return try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentUserOrNull()?.id
                ?: return AuthResult.Error("Sign up failed")

            val profile = InsertProfile(id = userId, displayName = displayName)
            client.postgrest.from("profiles").insert(profile)

            val user = User(
                id = userId,
                email = email,
                displayName = displayName,
            )
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign up failed")
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = fetchCurrentUser()
                ?: return AuthResult.Error("Could not load profile")
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign in failed")
        }
    }

    override suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (_: Exception) {}
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun getCurrentUser(): User? = fetchCurrentUser()

    override fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    override suspend fun createConversation(title: String, scenarioId: String?): Conversation {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val row = InsertConversation(userId = userId, title = title, scenarioId = scenarioId)
        val result = client.postgrest.from("conversations")
            .insert(row) { select() }
            .decodeSingle<Conversation>()
        return result
    }

    override suspend fun getConversations(): List<Conversation> {
        return client.postgrest.from("conversations")
            .select {
                order("last_message_at", Order.DESCENDING)
            }
            .decodeList<Conversation>()
    }

    override suspend fun deleteConversation(conversationId: String) {
        client.postgrest.from("conversations")
            .delete { filter { eq("id", conversationId) } }
    }

    override suspend fun getMessages(conversationId: String): List<ChatMessage> {
        val rows = client.postgrest.from("messages")
            .select {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<MessageRow>()

        return rows.map { row ->
            ChatMessage(
                id = row.id ?: "",
                role = when (row.role) {
                    "user" -> com.speakmind.app.feature.chat.domain.model.MessageRole.USER
                    "assistant" -> com.speakmind.app.feature.chat.domain.model.MessageRole.ASSISTANT
                    else -> com.speakmind.app.feature.chat.domain.model.MessageRole.SYSTEM
                },
                content = row.content,
            )
        }
    }

    override suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        val row = MessageRow(
            conversationId = conversationId,
            role = when (message.role) {
                com.speakmind.app.feature.chat.domain.model.MessageRole.USER -> "user"
                com.speakmind.app.feature.chat.domain.model.MessageRole.ASSISTANT -> "assistant"
                com.speakmind.app.feature.chat.domain.model.MessageRole.SYSTEM -> "system"
            },
            content = message.content,
        )
        client.postgrest.from("messages").insert(row)

        // Update conversation preview
        client.postgrest.from("conversations")
            .update({
                set("last_message_preview", message.content.take(100))
                set("message_count", conversationId) // will be fixed with proper increment
            }) {
                filter { eq("id", conversationId) }
            }
    }

    override suspend fun updateProfile(user: User) {
        client.postgrest.from("profiles")
            .update({
                set("display_name", user.displayName)
                set("level", user.level)
                set("streak_days", user.streakDays)
                set("total_vocab_learned", user.totalVocabLearned)
                set("total_conversations", user.totalConversations)
                set("total_minutes", user.totalMinutes)
            }) {
                filter { eq("id", user.id) }
            }
    }

    private suspend fun fetchCurrentUser(): User? {
        val authUser = client.auth.currentUserOrNull() ?: return null
        return try {
            val profile = client.postgrest.from("profiles")
                .select { filter { eq("id", authUser.id) } }
                .decodeSingle<ProfileRow>()

            User(
                id = profile.id,
                email = authUser.email ?: "",
                displayName = profile.displayName,
                level = profile.level,
                streakDays = profile.streakDays,
                totalVocabLearned = profile.totalVocabLearned,
                totalConversations = profile.totalConversations,
                totalMinutes = profile.totalMinutes,
            )
        } catch (_: Exception) {
            null
        }
    }
}
