package com.speakmind.app.core.network.impl

import com.speakmind.app.core.domain.model.PresenceState
import com.speakmind.app.core.network.RealtimeService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class SupabaseRealtimeService(
    private val client: SupabaseClient,
) : RealtimeService {

    private var currentChannelId: String? = null
    private val _typingState = MutableStateFlow(false)
    private val _presenceState = MutableStateFlow(PresenceState(userId = ""))

    override suspend fun joinConversation(conversationId: String) {
        try {
            currentChannelId = conversationId
            val channel = client.realtime.channel("chat:$conversationId")
            channel.subscribe()
        } catch (_: Exception) {}
    }

    override suspend fun leaveConversation() {
        try {
            val channelId = currentChannelId ?: return
            val channel = client.realtime.channel("chat:$channelId")
            channel.unsubscribe()
            currentChannelId = null
        } catch (_: Exception) {}
    }

    override suspend fun sendTypingIndicator(conversationId: String, isTyping: Boolean) {
        try {
            val channel = client.realtime.channel("chat:$conversationId")
            channel.broadcast("typing", buildJsonObject { put("isTyping", JsonPrimitive(isTyping)) })
        } catch (_: Exception) {}
    }

    override fun observeTypingState(conversationId: String): Flow<Boolean> {
        return _typingState.asStateFlow()
    }

    override suspend fun setOnline() {
        try {
            val userId = client.auth.currentUserOrNull()?.id ?: return
            _presenceState.value = _presenceState.value.copy(
                userId = userId,
                isOnline = true,
            )
            val channel = client.realtime.channel("presence")
            channel.subscribe()
        } catch (_: Exception) {}
    }

    override suspend fun setOffline() {
        try {
            _presenceState.value = _presenceState.value.copy(isOnline = false)
            val channel = client.realtime.channel("presence")
            channel.unsubscribe()
        } catch (_: Exception) {}
    }

    override fun observePresence(): Flow<PresenceState> {
        return _presenceState.asStateFlow()
    }
}
