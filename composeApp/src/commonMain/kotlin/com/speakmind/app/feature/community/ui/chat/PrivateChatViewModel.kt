package com.speakmind.app.feature.community.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.ContentModerator
import com.speakmind.app.feature.community.data.ViolationType
import com.speakmind.app.feature.community.data.model.ChannelMessage
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelUiState(
    val messages: List<ChannelMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val currentUserId: String = "",
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val dailyLimitReached: Boolean = false,
    val contentWarning: String? = null,
)

class ChannelViewModel(
    private val communityRepository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val sessionStartSeconds = System.currentTimeMillis() / 1000L

    init {
        _uiState.update { it.copy(currentUserId = communityRepository.currentUserId() ?: "") }
        loadInitialMessages()
        observeNewMessages()
        startHeartbeat()
        syncPending()
    }

    private fun loadInitialMessages() {
        viewModelScope.launch {
            try {
                val page = communityRepository.loadChannelPage(PAGE_SIZE, null)
                _uiState.update { it.copy(
                    messages = page,
                    hasMore = page.size >= PAGE_SIZE,
                    isInitialLoading = false,
                ) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isInitialLoading = false) }
            }
        }
    }

    private fun observeNewMessages() {
        viewModelScope.launch {
            communityRepository.observeNewChannelMessages(sessionStartSeconds).collect { newMsg ->
                _uiState.update { state ->
                    val idx = state.messages.indexOfFirst { it.id == newMsg.id }
                    val updated = if (idx >= 0) {
                        state.messages.toMutableList().also { it[idx] = newMsg }
                    } else {
                        (state.messages + newMsg).distinctBy { it.id }
                    }
                    state.copy(messages = updated)
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.messages.isEmpty()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        val oldestTs = state.messages.minOf { it.timestamp }
        viewModelScope.launch {
            try {
                val page = communityRepository.loadChannelPage(PAGE_SIZE, oldestTs)
                _uiState.update { it.copy(
                    messages = (page + it.messages).distinctBy { msg -> msg.id },
                    hasMore = page.size >= PAGE_SIZE,
                    isLoadingMore = false,
                ) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSendClicked() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val moderation = ContentModerator.check(text)
        if (!moderation.isAllowed) {
            _uiState.update { it.copy(
                inputText = "",
                contentWarning = moderation.violationType?.userMessage
                    ?: "This message violates community guidelines.",
            ) }
            return
        }
        viewModelScope.launch {
            val allowed = communityRepository.checkAndIncrementDailyQuota()
            if (!allowed) {
                _uiState.update { it.copy(dailyLimitReached = true) }
                return@launch
            }
            _uiState.update { it.copy(inputText = "", isSending = true) }
            try {
                val msg = communityRepository.sendChannelMessage(text)
                if (msg != null) {
                    _uiState.update { state ->
                        state.copy(
                            messages = (state.messages + msg).distinctBy { it.id },
                            isSending = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSending = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun onDailyLimitDismissed() {
        _uiState.update { it.copy(dailyLimitReached = false) }
    }

    fun onContentWarningDismissed() {
        _uiState.update { it.copy(contentWarning = null) }
    }

    fun onBack() {
        navigationManager.back()
    }

    private fun startHeartbeat() {
        viewModelScope.launch {
            while (true) {
                try { communityRepository.updateLastSeen() } catch (_: Exception) {}
                delay(30_000)
            }
        }
    }

    private fun syncPending() {
        viewModelScope.launch {
            try { communityRepository.syncPendingChannelMessages() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
