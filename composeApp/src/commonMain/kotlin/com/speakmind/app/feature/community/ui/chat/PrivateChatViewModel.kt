package com.speakmind.app.feature.community.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.ChatMessage
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.chatIdFor
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PrivateChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val currentUserId: String = "",
    val dailyLimitReached: Boolean = false,
)

class PrivateChatViewModel(
    private val communityRepository: CommunityRepository,
    private val navigationManager: NavigationManager,
    private val otherUserId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateChatUiState())
    val uiState: StateFlow<PrivateChatUiState> = _uiState.asStateFlow()

    private val chatId: String

    init {
        val currentUid = communityRepository.currentUserId() ?: ""
        chatId = chatIdFor(currentUid, otherUserId)
        _uiState.value = _uiState.value.copy(currentUserId = currentUid)
        observeMessages()
        syncPending()
        startOnlineHeartbeat()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            communityRepository.getMessages(chatId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
                // Reset unread whenever messages arrive while screen is open
                communityRepository.markChatRead(chatId)
            }
        }
    }

    private fun syncPending() {
        viewModelScope.launch {
            try { communityRepository.syncPendingMessages() } catch (_: Exception) {}
        }
    }

    private fun startOnlineHeartbeat() {
        viewModelScope.launch {
            while (true) {
                try { communityRepository.updateLastSeen() } catch (_: Exception) {}
                delay(30_000)
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSendClicked() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val allowed = communityRepository.checkAndIncrementDailyQuota()
            if (!allowed) {
                _uiState.value = _uiState.value.copy(dailyLimitReached = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(inputText = "", isSending = true)
            try {
                communityRepository.sendMessage(chatId, text)
            } catch (_: Exception) {}
            _uiState.value = _uiState.value.copy(isSending = false)
        }
    }

    fun onDailyLimitDismissed() {
        _uiState.value = _uiState.value.copy(dailyLimitReached = false)
    }

    fun onBack() {
        navigationManager.back()
    }
}
