package com.speakmind.app.feature.community.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.SPEAKY_HUB_ROOM_ID
import com.speakmind.app.feature.community.data.repository.BlockedException
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.navigation.CommunityAuthDestination
import com.speakmind.app.navigation.CommunityHubDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.RoomDestination
import io.github.aakira.napier.Napier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HubTab { CHATS, PEOPLE }

data class CommunityHubUiState(
    val tab: HubTab = HubTab.CHATS,
    val isGuest: Boolean = false,
    val rooms: List<ChatRoom> = emptyList(),
    val isLoadingRooms: Boolean = true,
    val people: List<CommunityUser> = emptyList(),
    val peopleQuery: String = "",
    val isLoadingPeople: Boolean = false,
    val hasMorePeople: Boolean = true,
    val onlineUserIds: Set<String> = emptySet(),
    val blockedUserIds: Set<String> = emptySet(),
    val confirmDeleteAccount: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class CommunityHubViewModel(
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityHubUiState(isGuest = repository.isGuest()))
    val uiState: StateFlow<CommunityHubUiState> = _uiState.asStateFlow()

    private val activity = MutableStateFlow(0)
    private var peopleSearchJob: Job? = null

    init {
        refreshRooms()
        viewModelScope.launch {
            repository.onlineUserIds.collect { ids -> _uiState.update { it.copy(onlineUserIds = ids) } }
        }
        viewModelScope.launch {
            repository.refreshBlocks()
            repository.blockedUserIds.collect { ids -> _uiState.update { it.copy(blockedUserIds = ids) } }
        }
        viewModelScope.launch {
            try {
                repository.observeRoomActivity().collect { activity.value++ }
            } catch (e: Exception) {
                Napier.w("Room activity stream failed", e)
            }
        }
        viewModelScope.launch {
            activity.debounce(600).collect { if (it > 0) refreshRooms() }
        }
    }

    /** Called whenever the hub becomes visible again (e.g. back from a chat). */
    fun onResume() {
        refreshRooms()
        when (_uiState.value.tab) {
            HubTab.PEOPLE -> loadPeople(reset = true)
            HubTab.CHATS -> Unit
        }
    }

    fun onTabSelected(tab: HubTab) {
        _uiState.update { it.copy(tab = tab) }
        when (tab) {
            HubTab.CHATS -> refreshRooms()
            HubTab.PEOPLE -> if (_uiState.value.people.isEmpty()) loadPeople(reset = true)
        }
    }

    private fun refreshRooms() {
        viewModelScope.launch {
            try {
                // Inbox = private chats; Speaky Hub is pinned separately
                val rooms = repository.myRooms().filter { it.kind == RoomKind.DIRECT }
                _uiState.update { it.copy(rooms = rooms, isLoadingRooms = false) }
            } catch (e: Exception) {
                Napier.w("myRooms failed", e)
                _uiState.update { it.copy(isLoadingRooms = false, error = friendlyCommunityError(e, "Couldn't load chats.")) }
            }
        }
    }

    // ---------- People ----------

    fun onPeopleQueryChanged(query: String) {
        _uiState.update { it.copy(peopleQuery = query) }
        peopleSearchJob?.cancel()
        peopleSearchJob = viewModelScope.launch {
            delay(300)
            loadPeople(reset = true)
        }
    }

    fun loadMorePeople() {
        val s = _uiState.value
        if (s.isLoadingPeople || !s.hasMorePeople) return
        loadPeople(reset = false)
    }

    private fun loadPeople(reset: Boolean) {
        val offset = if (reset) 0 else _uiState.value.people.size
        _uiState.update { it.copy(isLoadingPeople = true) }
        viewModelScope.launch {
            try {
                val page = repository.searchUsers(_uiState.value.peopleQuery, offset, PEOPLE_PAGE)
                _uiState.update {
                    it.copy(
                        people = if (reset) page else (it.people + page).distinctBy { u -> u.uid },
                        hasMorePeople = page.size >= PEOPLE_PAGE,
                        isLoadingPeople = false,
                    )
                }
            } catch (e: Exception) {
                Napier.w("searchUsers failed", e)
                _uiState.update { it.copy(isLoadingPeople = false) }
            }
        }
    }

    fun onPersonClicked(user: CommunityUser) {
        if (requireAccount("message people privately")) return
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

    /** Guests can only use Speaky Hub; returns true (and explains) if the action is blocked. */
    private fun requireAccount(action: String): Boolean {
        if (!_uiState.value.isGuest) return false
        _uiState.update { it.copy(error = "Sign in with Google to $action.") }
        return true
    }

    // ---------- Account ----------

    /** Guests upgrade by signing in with Google (a fresh account). */
    fun onSignInWithGoogle() {
        viewModelScope.launch {
            try {
                repository.signOut()
            } catch (_: Exception) {
            }
            navigationManager.navigate(CommunityAuthDestination) {
                popUpTo<CommunityHubDestination> { inclusive = true }
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            try {
                repository.signOut()
            } catch (e: Exception) {
                Napier.w("signOut failed", e)
            }
            navigationManager.back()
        }
    }

    fun onDeleteAccountRequested() = _uiState.update { it.copy(confirmDeleteAccount = true) }
    fun onDeleteAccountDismissed() = _uiState.update { it.copy(confirmDeleteAccount = false) }

    fun onConfirmDeleteAccount() {
        _uiState.update { it.copy(confirmDeleteAccount = false, isDeletingAccount = true) }
        viewModelScope.launch {
            try {
                repository.deleteAccount()
                navigationManager.back()
            } catch (e: Exception) {
                Napier.w("deleteAccount failed", e)
                _uiState.update {
                    it.copy(isDeletingAccount = false, error = friendlyCommunityError(e, "Couldn't delete your account. Try again."))
                }
            }
        }
    }

    // ---------- Navigation ----------

    fun openSpeakyHub() = navigationManager.navigate(RoomDestination(SPEAKY_HUB_ROOM_ID, "Speaky Hub"))

    fun openRoom(room: ChatRoom) = navigationManager.navigate(RoomDestination(room.id, room.displayName))

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun onBack() = navigationManager.back()

    /** Screen visibility drives the user's online status. */
    fun onVisibilityChanged(visible: Boolean) = repository.setActive(visible)

    companion object {
        private const val PEOPLE_PAGE = 30
    }
}
