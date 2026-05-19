package com.speakmind.app.feature.community.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.PrivateChatDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

data class CommunityUsersUiState(
    val users: List<CommunityUser> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String? = null,
    val unreadCounts: Map<String, Int> = emptyMap(),
)

class CommunityUsersViewModel(
    private val communityRepository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUsersUiState())
    val uiState: StateFlow<CommunityUsersUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        _uiState.value = _uiState.value.copy(
            currentUserId = communityRepository.currentUserId(),
        )
        updateLastSeen()
        observeUnreadCounts()
        observeUsers()
    }

    private fun observeUsers() {
        viewModelScope.launch {
            searchQuery
                .flatMapLatest { query ->
                    flow {
                        // Immediate load for empty query, debounce for typed searches
                        if (query.isNotBlank()) delay(300)
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                        communityRepository.getUsers(searchQuery = query).collect { emit(it) }
                    }
                }
                .collect { users ->
                    _uiState.value = _uiState.value.copy(users = users, isLoading = false)
                }
        }
    }

    private fun observeUnreadCounts() {
        viewModelScope.launch {
            communityRepository.getUnreadCounts().collect { counts ->
                _uiState.value = _uiState.value.copy(unreadCounts = counts)
            }
        }
    }

    private fun updateLastSeen() {
        viewModelScope.launch {
            try { communityRepository.updateLastSeen() } catch (_: Exception) {}
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchQuery.value = query
    }

    fun onUserClicked(user: CommunityUser) {
        navigationManager.navigate(
            PrivateChatDestination(
                otherUserId = user.uid,
                otherNickname = user.nickname,
                otherGender = user.gender,
                otherPhotoUrl = user.photoUrl ?: "",
            )
        )
    }

    fun onRefresh() {
        searchQuery.value = searchQuery.value // re-triggers observeUsers
    }
}
