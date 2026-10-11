package com.speakmind.app.feature.community.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.ContentModerator
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.GroupMember
import com.speakmind.app.feature.community.data.model.GroupRole
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.navigation.CommunityHubDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.RoomDestination
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupInfoUiState(
    val room: ChatRoom? = null,
    val members: List<GroupMember> = emptyList(),
    val onlineUserIds: Set<String> = emptySet(),
    val currentUserId: String = "",
    val isLoading: Boolean = true,
    val showEdit: Boolean = false,
    val confirmLeave: Boolean = false,
    val confirmDelete: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val myRole: GroupRole? get() = room?.myRole
    val canManage: Boolean get() = room?.canManage == true
    val isOwner: Boolean get() = myRole == GroupRole.OWNER
}

class GroupInfoViewModel(
    private val roomId: String,
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupInfoUiState(currentUserId = repository.currentUserId() ?: ""))
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            repository.onlineUserIds.collect { ids -> _uiState.update { it.copy(onlineUserIds = ids) } }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                val room = repository.getRoom(roomId)
                val members = repository.roomMembers(roomId)
                _uiState.update { it.copy(room = room, members = members, isLoading = false) }
            } catch (e: Exception) {
                Napier.w("Group info load failed", e)
                _uiState.update { it.copy(isLoading = false, error = friendlyCommunityError(e, "Couldn't load the group.")) }
            }
        }
    }

    fun onShowEdit() = _uiState.update { it.copy(showEdit = true) }
    fun onDismissEdit() = _uiState.update { it.copy(showEdit = false) }

    fun onSaveEdit(name: String, description: String, isPrivate: Boolean) {
        val trimmed = name.trim()
        if (trimmed.length !in 2..40) return setError("Group name must be 2–40 characters")
        if (!ContentModerator.check("$trimmed $description").isAllowed) return setError("Please choose a different name")
        _uiState.update { it.copy(showEdit = false) }
        act("Couldn't save changes.", success = "Group updated.") {
            repository.updateGroup(roomId, trimmed, description, isPrivate)
            _uiState.update { s ->
                s.copy(room = s.room?.copy(name = trimmed, description = description.trim(), isPrivate = isPrivate))
            }
        }
    }

    fun onRegenerateCode() = act("Couldn't create a new code.", success = "New invite code created. The old one no longer works.") {
        val code = repository.regenerateInviteCode(roomId)
        _uiState.update { s -> s.copy(room = s.room?.copy(inviteCode = code)) }
    }

    fun onCodeCopied() = _uiState.update { it.copy(info = "Invite code copied.") }

    fun onToggleMute() {
        val muted = !(_uiState.value.room?.muted ?: false)
        act("Couldn't change notifications.", success = if (muted) "Notifications muted." else "Notifications on.") {
            repository.setMuted(roomId, muted)
            _uiState.update { s -> s.copy(room = s.room?.copy(muted = muted)) }
        }
    }

    fun onRemoveMember(member: GroupMember) = act("Couldn't remove ${member.user.nickname}.", success = "${member.user.nickname} removed.") {
        repository.removeMember(roomId, member.user.uid)
        _uiState.update { s -> s.copy(members = s.members.filterNot { it.user.uid == member.user.uid }) }
    }

    fun onSetRole(member: GroupMember, role: GroupRole) = act("Couldn't change the role.") {
        repository.setMemberRole(roomId, member.user.uid, role)
        _uiState.update { s ->
            s.copy(members = s.members.map { if (it.user.uid == member.user.uid) it.copy(role = role) else it })
        }
    }

    fun onMessageMember(user: CommunityUser) {
        if (user.uid == _uiState.value.currentUserId || repository.isGuest()) return
        act("Couldn't open the chat.") {
            val dm = repository.openDirectChat(user.uid)
            navigationManager.navigate(RoomDestination(dm, user.nickname))
        }
    }

    fun onLeaveRequested() = _uiState.update { it.copy(confirmLeave = true) }
    fun onDeleteRequested() = _uiState.update { it.copy(confirmDelete = true) }
    fun onConfirmDismissed() = _uiState.update { it.copy(confirmLeave = false, confirmDelete = false) }

    fun onConfirmLeave() {
        _uiState.update { it.copy(confirmLeave = false) }
        act("Couldn't leave the group.") {
            repository.leaveRoom(roomId)
            backToHub()
        }
    }

    fun onConfirmDelete() {
        _uiState.update { it.copy(confirmDelete = false) }
        act("Couldn't delete the group.") {
            repository.deleteGroup(roomId)
            backToHub()
        }
    }

    private fun backToHub() {
        navigationManager.navigate(CommunityHubDestination) {
            popUpTo<CommunityHubDestination> { inclusive = false }
            launchSingleTop = true
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
    fun onInfoDismissed() = _uiState.update { it.copy(info = null) }
    fun onBack() = navigationManager.back()

    private fun setError(msg: String) = _uiState.update { it.copy(error = msg) }

    private fun act(fallback: String, success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (success != null) _uiState.update { it.copy(info = success) }
            } catch (e: Exception) {
                Napier.w("Group action failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, fallback)) }
            }
        }
    }
}
