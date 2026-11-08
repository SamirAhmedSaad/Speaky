package com.speakmind.app.feature.community.ui.people

import com.speakmind.app.feature.community.ui.components.openPrivateChat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.ui.room.draftDirectRoomId
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.feature.community.ui.auth.GoogleIdResult
import com.speakmind.app.feature.community.ui.auth.SignInOutcome
import com.speakmind.app.feature.community.ui.auth.friendlySignInError
import com.speakmind.app.feature.community.ui.auth.signInWithGoogleInPlace
import com.speakmind.app.navigation.CommunitySetupDestination
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

/** "All", "Online", or a CEFR level. */
val PEOPLE_FILTERS = listOf("All", "Online", "A1", "A2", "B1", "B2", "C1")

data class PeopleUiState(
    val people: List<CommunityUser> = emptyList(),
    val query: String = "",
    val filter: String = "All",
    val profileUser: CommunityUser? = null,
    val info: String? = null,
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val onlineUserIds: Set<String> = emptySet(),
    val blockedUserIds: Set<String> = emptySet(),
    val isGuest: Boolean = true,
    val isSigningIn: Boolean = false,
    val isAdmin: Boolean = false,
    /** Only the owner adds or removes moderators. */
    val isOwner: Boolean = false,
    val moderatorIds: Set<String> = emptySet(),
    val error: String? = null,
    /** Admin: who is about to be banned. */
    val banTarget: CommunityUser? = null,
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
        viewModelScope.launch {
            // The saved login may still be loading on a cold start.
            repository.hasSession()
            _uiState.update { it.copy(isGuest = repository.isGuest()) }
        }
        load(reset = true)
        viewModelScope.launch {
            repository.onlineUserIds.collect { ids ->
                val changed = ids != _uiState.value.onlineUserIds
                _uiState.update { it.copy(onlineUserIds = ids) }
                // The Online tab follows people coming and going, quietly (no spinner, no full reload).
                if (changed && _uiState.value.filter == "Online") syncOnline(ids)
            }
        }
        loadModerators()
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

    fun onFilterSelected(filter: String) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter, people = emptyList()) }
        load(reset = true)
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoading || !s.hasMore) return
        load(reset = false)
    }

    private var loadJob: Job? = null

    private fun load(reset: Boolean) {
        // A new search/filter replaces any page still loading, so a slow "All" result can't
        // land on the Online tab.
        if (reset) loadJob?.cancel()
        val offset = if (reset) 0 else _uiState.value.people.size
        _uiState.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            try {
                val state = _uiState.value
                val page = repository.searchUsers(
                    query = state.query,
                    offset = offset,
                    pageSize = PAGE,
                    level = state.filter.takeIf { it.length == 2 },
                    onlyIds = if (state.filter == "Online") state.onlineUserIds else null,
                )
                _uiState.update {
                    // Ignore a page for a filter or search that is no longer selected.
                    if (it.filter != state.filter || it.query != state.query) return@update it
                    it.copy(
                        people = if (reset) page else (it.people + page).distinctBy { u -> u.uid },
                        hasMore = page.size >= PAGE,
                        isLoading = false,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("searchUsers failed", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Online tab: drops people who left, fetches only the ones who just came online. */
    private fun syncOnline(ids: Set<String>) {
        val state = _uiState.value
        if (state.isLoading) return load(reset = true)
        val kept = state.people.filter { it.uid in ids }
        val added = ids - state.people.map { it.uid }.toSet()
        _uiState.update { it.copy(people = kept) }
        if (added.isEmpty()) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val page = repository.searchUsers(query = state.query, offset = 0, pageSize = PAGE, level = null, onlyIds = added)
                _uiState.update {
                    if (it.filter != "Online" || it.query != state.query) return@update it
                    it.copy(people = (it.people + page).distinctBy { u -> u.uid }.filter { u -> u.uid in it.onlineUserIds })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("searchUsers (online) failed", e)
            }
        }
    }

    fun onPersonClicked(user: CommunityUser) = _uiState.update { it.copy(profileUser = user) }
    fun onProfileDismissed() = _uiState.update { it.copy(profileUser = null) }

    fun onToggleBlock(user: CommunityUser) {
        _uiState.update { it.copy(profileUser = null) }
        viewModelScope.launch {
            try {
                if (user.uid in _uiState.value.blockedUserIds) {
                    repository.unblockUser(user.uid)
                    _uiState.update { it.copy(info = "${user.nickname} is unblocked.") }
                } else {
                    repository.blockUser(user.uid)
                    _uiState.update { it.copy(info = "${user.nickname} is blocked.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't update block.")) }
            }
        }
    }

    private fun loadModerators() {
        viewModelScope.launch {
            if (!repository.isAdmin()) return@launch
            val ids = try {
                repository.adminModerators().map { it.uid }.toSet()
            } catch (e: Exception) {
                Napier.w("adminModerators failed", e)
                emptySet()
            }
            _uiState.update { it.copy(isAdmin = true, moderatorIds = ids, isOwner = repository.isCommunityOwner()) }
        }
    }

    fun onBanRequested(user: CommunityUser) = _uiState.update { it.copy(profileUser = null, banTarget = user) }
    fun onBanCancelled() = _uiState.update { it.copy(banTarget = null) }

    fun onConfirmBan(removeMessages: Boolean) {
        val user = _uiState.value.banTarget ?: return
        _uiState.update { it.copy(banTarget = null) }
        viewModelScope.launch {
            try {
                repository.adminBan(user.uid, "other", removeMessages)
                _uiState.update { it.copy(info = "${user.nickname} is banned.") }
            } catch (e: Exception) {
                Napier.w("adminBan failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't ban.")) }
            }
        }
    }

    suspend fun isUserBanned(userId: String): Boolean = repository.isUserBanned(userId)

    fun onAdminUnban(user: CommunityUser) {
        _uiState.update { it.copy(profileUser = null) }
        viewModelScope.launch {
            try {
                repository.adminUnban(user.uid)
                _uiState.update { it.copy(info = "${user.nickname} is unbanned.") }
            } catch (e: Exception) {
                Napier.w("adminUnban failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't unban.")) }
            }
        }
    }

    fun onRemovePhoto(user: CommunityUser) {
        _uiState.update { it.copy(profileUser = null) }
        viewModelScope.launch {
            try {
                repository.adminRejectPhoto(user.uid)
                _uiState.update { s ->
                    s.copy(
                        people = s.people.map { if (it.uid == user.uid) it.copy(photoUrl = null) else it },
                        info = "${user.nickname}'s photo was removed. They'll be asked for a modest one.",
                    )
                }
            } catch (e: Exception) {
                Napier.w("adminRejectPhoto failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't remove the photo.")) }
            }
        }
    }

    fun onToggleModerator(user: CommunityUser) {
        val makeModerator = user.uid !in _uiState.value.moderatorIds
        _uiState.update { it.copy(profileUser = null) }
        viewModelScope.launch {
            try {
                repository.adminSetModerator(user.uid, makeModerator)
                _uiState.update {
                    it.copy(
                        moderatorIds = if (makeModerator) it.moderatorIds + user.uid else it.moderatorIds - user.uid,
                        info = if (makeModerator) "${user.nickname} is now a moderator." else "${user.nickname} is no longer a moderator.",
                    )
                }
            } catch (e: Exception) {
                Napier.w("adminSetModerator failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't update moderator.")) }
            }
        }
    }

    /** Premium, or free private messages left today. */
    fun onMessageClicked(user: CommunityUser) {
        viewModelScope.launch { repository.openPrivateChat("Message ${user.nickname}") { onMessage(user) } }
    }

    fun onMessage(user: CommunityUser) {
        _uiState.update { it.copy(profileUser = null) }
        if (_uiState.value.isGuest) {
            _uiState.update { it.copy(error = "Sign in with Google first to send private messages.") }
            return
        }
        navigationManager.navigate(RoomDestination(roomId = draftDirectRoomId(user.uid), title = user.nickname))
    }

    /** Opens Google's account picker right here; first-timers then pick a nickname. */
    fun onGoogleSignIn(launchPicker: suspend () -> GoogleIdResult) {
        if (_uiState.value.isSigningIn) return
        _uiState.update { it.copy(isSigningIn = true) }
        viewModelScope.launch {
            try {
                when (signInWithGoogleInPlace(repository, launchPicker)) {
                    SignInOutcome.NEEDS_PROFILE -> navigationManager.navigate(CommunitySetupDestination)
                    SignInOutcome.READY -> onSignedIn()
                    SignInOutcome.CANCELLED -> Unit
                }
            } catch (e: Exception) {
                Napier.w("Google sign-in failed", e)
                _uiState.update { it.copy(error = friendlySignInError(e)) }
            } finally {
                _uiState.update { it.copy(isSigningIn = false) }
            }
        }
    }

    private fun onSignedIn() {
        _uiState.update { it.copy(isGuest = repository.isGuest()) }
        repository.setActive(true)
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null, info = null) }
    fun onBack() = navigationManager.back()
    fun onVisibilityChanged(visible: Boolean) = repository.setActive(visible)

    companion object {
        private const val PAGE = 30
    }
}
