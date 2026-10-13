package com.speakmind.app.feature.community.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.SPEAKY_HUB_ROOM_ID
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.feature.community.data.ContentModerator
import com.speakmind.app.feature.profile.domain.NameValidationResult
import com.speakmind.app.feature.profile.domain.NameValidator
import com.speakmind.app.navigation.AdminReportsDestination
import com.speakmind.app.feature.community.ui.auth.GoogleIdResult
import com.speakmind.app.feature.community.ui.auth.SignInOutcome
import com.speakmind.app.feature.community.ui.auth.friendlySignInError
import com.speakmind.app.feature.community.ui.auth.signInWithGoogleInPlace
import com.speakmind.app.navigation.CommunitySetupDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.PeopleDestination
import com.speakmind.app.navigation.RoomDestination
import io.github.aakira.napier.Napier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityHubUiState(
    val isGuest: Boolean = true,
    val onlineUserIds: Set<String> = emptySet(),
    /** The pinned general chat. */
    val hubRoom: ChatRoom? = null,
    /** Private chats, newest first. */
    val chats: List<ChatRoom> = emptyList(),
    val isLoadingChats: Boolean = true,
    val isLoadingMoreChats: Boolean = false,
    val hasMoreChats: Boolean = false,
    /** Unread private messages across all chats (not just the loaded page). */
    val unreadTotal: Int = 0,
    val isSigningIn: Boolean = false,
    val isAdmin: Boolean = false,
    val myNickname: String = "",
    val myGender: String = "",
    val myPhotoUrl: String? = null,
    val showEditProfile: Boolean = false,
    val isSavingProfile: Boolean = false,
    val profileError: String? = null,
    val confirmDeleteAccount: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val error: String? = null,
) {

}

/** Community inbox: Speaky Hub pinned on top, then private chats. People is in the top bar. */
@OptIn(FlowPreview::class)
class CommunityHubViewModel(
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityHubUiState(isGuest = repository.isGuest()))
    val uiState: StateFlow<CommunityHubUiState> = _uiState.asStateFlow()

    private val activity = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // The saved login may still be loading on a cold start (e.g. opened from a notification).
            repository.hasSession()
            _uiState.update { it.copy(isGuest = repository.isGuest()) }
        }
        viewModelScope.launch {
            repository.onlineUserIds.collect { ids -> _uiState.update { it.copy(onlineUserIds = ids) } }
        }
        viewModelScope.launch {
            try {
                repository.observeRoomActivity().collect { activity.value++ }
            } catch (e: Exception) {
                Napier.w("Room activity stream failed", e)
            }
        }
        viewModelScope.launch {
            activity.debounce(500).collect { if (it > 0) refresh() }
        }
    }

    /** Called whenever the page becomes visible again (e.g. back from a chat). */
    fun onResume() {
        viewModelScope.launch {
            repository.hasSession()
            _uiState.update { it.copy(isGuest = repository.isGuest()) }
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(hubRoom = repository.getRoom(SPEAKY_HUB_ROOM_ID)) }
            } catch (e: Exception) {
                Napier.w("Hub preview failed", e)
            }
        }
        if (_uiState.value.isGuest) {
            _uiState.update { it.copy(chats = emptyList(), isLoadingChats = false) }
            return
        }
        viewModelScope.launch {
            val profile = repository.getLocalProfile()
            val admin = repository.isAdmin()
            _uiState.update {
                it.copy(
                    isAdmin = admin,
                    myNickname = profile?.nickname.orEmpty(),
                    myGender = profile?.gender.orEmpty(),
                    myPhotoUrl = profile?.photoUrl,
                )
            }
        }
        viewModelScope.launch {
            val unread = repository.unreadPrivateCount()
            _uiState.update { it.copy(unreadTotal = unread) }
        }
        viewModelScope.launch {
            try {
                // A live refresh keeps the pages already scrolled through (up to the server's max of 100).
                val size = _uiState.value.chats.size.coerceIn(PAGE, 100)
                val chats = repository.directChats(pageSize = size, after = null)
                _uiState.update { it.copy(chats = chats, isLoadingChats = false, hasMoreChats = chats.size >= size) }
            } catch (e: Exception) {
                Napier.w("Inbox load failed", e)
                _uiState.update { it.copy(isLoadingChats = false, error = friendlyCommunityError(e, "Couldn't load your messages.")) }
            }
        }
    }

    fun loadMoreChats() {
        val state = _uiState.value
        if (state.isLoadingChats || state.isLoadingMoreChats || !state.hasMoreChats) return
        val last = state.chats.lastOrNull() ?: return
        _uiState.update { it.copy(isLoadingMoreChats = true) }
        viewModelScope.launch {
            try {
                val page = repository.directChats(pageSize = PAGE, after = last)
                _uiState.update {
                    it.copy(
                        chats = (it.chats + page).distinctBy { c -> c.id },
                        hasMoreChats = page.size >= PAGE,
                        isLoadingMoreChats = false,
                    )
                }
            } catch (e: Exception) {
                Napier.w("Inbox page failed", e)
                _uiState.update { it.copy(isLoadingMoreChats = false) }
            }
        }
    }

    fun openSpeakyHub() = navigationManager.navigate(RoomDestination(SPEAKY_HUB_ROOM_ID, "Speaky Hub"))
    fun openChat(room: ChatRoom) = navigationManager.navigate(RoomDestination(room.id, room.displayName))
    fun openPeople() = navigationManager.navigate(PeopleDestination)

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
        repository.setActive(true)
        onResume()
    }

    fun openReports() = navigationManager.navigate(AdminReportsDestination)

    fun onEditProfile() = _uiState.update { it.copy(showEditProfile = true, profileError = null) }
    fun onEditProfileDismissed() = _uiState.update { it.copy(showEditProfile = false, profileError = null) }

    fun onSaveProfile(nickname: String, gender: String) {
        val name = nickname.trim()
        val check = NameValidator.validate(name)
        if (check != NameValidationResult.Valid) {
            _uiState.update { it.copy(profileError = NameValidator.errorMessage(check) ?: "Invalid name") }
            return
        }
        if (!ContentModerator.check(name).isAllowed) {
            _uiState.update { it.copy(profileError = "Please choose a different name") }
            return
        }
        _uiState.update { it.copy(isSavingProfile = true, profileError = null) }
        viewModelScope.launch {
            try {
                repository.updateProfile(name, gender)
                _uiState.update { it.copy(isSavingProfile = false, showEditProfile = false, myNickname = name, myGender = gender) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingProfile = false, profileError = friendlyCommunityError(e, "Couldn't save your profile.")) }
            }
        }
    }

    fun onPhotoPicked(bytes: ByteArray?) {
        if (bytes == null) return
        _uiState.update { it.copy(isSavingProfile = true, profileError = null) }
        viewModelScope.launch {
            try {
                val url = repository.uploadAvatar(bytes)
                _uiState.update { it.copy(isSavingProfile = false, myPhotoUrl = url) }
            } catch (e: Exception) {
                Napier.w("uploadAvatar failed", e)
                _uiState.update { it.copy(isSavingProfile = false, profileError = friendlyCommunityError(e, "Couldn't upload the photo.")) }
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
            _uiState.update { it.copy(isGuest = true, chats = emptyList()) }
        }
    }

    fun onDeleteAccountRequested() = _uiState.update { it.copy(confirmDeleteAccount = true) }
    fun onDeleteAccountDismissed() = _uiState.update { it.copy(confirmDeleteAccount = false) }

    fun onConfirmDeleteAccount() {
        _uiState.update { it.copy(confirmDeleteAccount = false, isDeletingAccount = true) }
        viewModelScope.launch {
            try {
                repository.deleteAccount()
                _uiState.update { it.copy(isDeletingAccount = false, isGuest = true, chats = emptyList()) }
            } catch (e: Exception) {
                Napier.w("deleteAccount failed", e)
                _uiState.update {
                    it.copy(isDeletingAccount = false, error = friendlyCommunityError(e, "Couldn't delete your account. Try again."))
                }
            }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
    fun onBack() = navigationManager.back()

    /** Screen visibility drives the user's online status. */
    fun onVisibilityChanged(visible: Boolean) = repository.setActive(visible)

    private companion object {
        const val PAGE = 30
    }
}
