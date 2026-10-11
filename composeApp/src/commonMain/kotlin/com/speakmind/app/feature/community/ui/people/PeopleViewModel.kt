package com.speakmind.app.feature.community.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.repository.BlockedException
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.navigation.CommunityAuthDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.RoomDestination
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeopleUiState(
    val people: List<CommunityUser> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val onlineUserIds: Set<String> = emptySet(),
    val blockedUserIds: Set<String> = emptySet(),
    val isGuest: Boolean = true,
    val error: String? = null,
)

/** Everyone in the community; tap a person to message them privately. */
class PeopleViewModel(
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState(isGuest = repository.isGuest()))
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load(reset = true)
        viewModelScope.launch {
            repository.onlineUserIds.collect { ids -> _uiState.update { it.copy(onlineUserIds = ids) } }
        }
        viewModelScope.launch {
            repository.refreshBlocks()
            repository.blockedUserIds.collect { ids -> _uiState.update { it.copy(blockedUserIds = ids) } }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            load(reset = true)
        }
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoading || !s.hasMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val offset = if (reset) 0 else _uiState.value.people.size
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val page = repository.searchUsers(_uiState.value.query, offset, PAGE)
                _uiState.update {
                    it.copy(
                        people = if (reset) page else (it.people + page).distinctBy { u -> u.uid },
                        hasMore = page.size >= PAGE,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Napier.w("searchUsers failed", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onPersonClicked(user: CommunityUser) {
        if (_uiState.value.isGuest) {
            navigationManager.navigate(CommunityAuthDestination)
            return
        }
        viewModelScope.launch {
            try {
                val roomId = repository.openDirectChat(user.uid)
                navigationManager.navigate(RoomDestination(roomId = roomId, title = user.nickname))
            } catch (e: BlockedException) {
                _uiState.update { it.copy(error = "You can't message ${user.nickname}.") }
            } catch (e: Exception) {
                Napier.w("openDirectChat failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't open the chat.")) }
            }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
    fun onBack() = navigationManager.back()
    fun onVisibilityChanged(visible: Boolean) = repository.setActive(visible)

    companion object {
        private const val PAGE = 30
    }
}
