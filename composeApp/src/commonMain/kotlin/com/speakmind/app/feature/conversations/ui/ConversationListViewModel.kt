package com.speakmind.app.feature.conversations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.core.domain.model.Conversation
import com.speakmind.app.core.network.BackendService
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class ConversationListViewModel(
    private val backendService: BackendService,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val conversations = backendService.getConversations()
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load conversations",
                )
            }
        }
    }

    fun onNewChat() {
        navigationManager.navigate(ChatDestination())
    }

    fun onOpenConversation(conversationId: String) {
        navigationManager.navigate(ChatDestination(conversationId = conversationId))
    }

    fun onDeleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                backendService.deleteConversation(conversationId)
                loadConversations()
            } catch (_: Exception) {}
        }
    }

    fun onNavigateBack() {
        navigationManager.back()
    }
}
