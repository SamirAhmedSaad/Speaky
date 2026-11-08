package com.speakmind.app.feature.community.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.AdminChat
import com.speakmind.app.feature.community.data.model.AdminChatMessage
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.navigation.AdminChatDestination
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CHATS_PAGE = 30
private const val MESSAGES_PAGE = 60

data class AdminChatsUiState(
    val chats: List<AdminChat> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

/** Admin-only list of every private chat, for moderation. */
class AdminChatsViewModel(
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminChatsUiState())
    val uiState: StateFlow<AdminChatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val chats = repository.adminPrivateChats(CHATS_PAGE, after = null)
                _uiState.update { it.copy(chats = chats, isLoading = false, hasMore = chats.size >= CHATS_PAGE) }
            } catch (e: Exception) {
                Napier.w("adminPrivateChats failed", e)
                _uiState.update { it.copy(isLoading = false, error = friendlyCommunityError(e, "Couldn't load chats.")) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val last = state.chats.lastOrNull() ?: return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repository.adminPrivateChats(CHATS_PAGE, after = last)
                _uiState.update {
                    it.copy(chats = (it.chats + page).distinctBy { c -> c.roomId }, hasMore = page.size >= CHATS_PAGE, isLoadingMore = false)
                }
            } catch (e: Exception) {
                Napier.w("adminPrivateChats page failed", e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onChatClicked(chat: AdminChat) = navigationManager.navigate(
        AdminChatDestination(
            roomId = chat.roomId,
            userAId = chat.userA.uid,
            userAName = chat.userA.nickname,
            userBId = chat.userB?.uid,
            userBName = chat.userB?.nickname ?: "Deleted account",
        )
    )

    fun onBack() = navigationManager.back()
}

data class AdminChatUiState(
    val messages: List<AdminChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val banTarget: Pair<String, String>? = null,
    val info: String? = null,
    val error: String? = null,
    /** The voice message playing now / loading. */
    val playingId: String? = null,
    val loadingVoiceId: String? = null,
    val deleteTarget: AdminChatMessage? = null,
)

/** One private chat, read-only, with ban, delete and voice playback for admins. */
class AdminChatViewModel(
    val destination: AdminChatDestination,
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
    private val voicePlayer: com.speakmind.app.feature.voice.platform.VoicePlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminChatUiState())
    val uiState: StateFlow<AdminChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val messages = repository.adminChatMessages(destination.roomId, MESSAGES_PAGE, before = null)
                _uiState.update { it.copy(messages = messages, isLoading = false, hasMore = messages.size >= MESSAGES_PAGE) }
            } catch (e: Exception) {
                Napier.w("adminChatMessages failed", e)
                _uiState.update { it.copy(isLoading = false, error = friendlyCommunityError(e, "Couldn't load messages.")) }
            }
        }
    }

    /** Older messages (the list is newest first). */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val oldest = state.messages.lastOrNull() ?: return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repository.adminChatMessages(destination.roomId, MESSAGES_PAGE, before = oldest)
                _uiState.update {
                    it.copy(messages = (it.messages + page).distinctBy { m -> m.id }, hasMore = page.size >= MESSAGES_PAGE, isLoadingMore = false)
                }
            } catch (e: Exception) {
                Napier.w("adminChatMessages page failed", e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onBanRequested(userId: String, name: String) = _uiState.update { it.copy(banTarget = userId to name) }
    fun onBanCancelled() = _uiState.update { it.copy(banTarget = null) }

    fun onConfirmBan(removeMessages: Boolean) {
        val (userId, name) = _uiState.value.banTarget ?: return
        _uiState.update { it.copy(banTarget = null) }
        viewModelScope.launch {
            try {
                repository.adminBan(userId, "other", removeMessages)
                _uiState.update { it.copy(info = "$name is banned.") }
            } catch (e: Exception) {
                Napier.w("ban from chat review failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Action failed.")) }
            }
        }
    }

    fun onMessageDismissed() = _uiState.update { it.copy(info = null, error = null) }

    /** Plays (or stops) a voice message. */
    fun onPlayVoice(message: AdminChatMessage) {
        val path = message.voicePath ?: return _uiState.update { it.copy(error = "Update the server to play voice messages.") }
        if (_uiState.value.playingId == message.id) {
            voicePlayer.stop()
            _uiState.update { it.copy(playingId = null) }
            return
        }
        voicePlayer.stop()
        _uiState.update { it.copy(loadingVoiceId = message.id, playingId = null) }
        viewModelScope.launch {
            try {
                val bytes = repository.downloadVoice(path)
                _uiState.update { it.copy(loadingVoiceId = null, playingId = message.id) }
                voicePlayer.playFromBytes(bytes)
            } catch (e: Exception) {
                Napier.w("admin voice playback failed", e)
                _uiState.update { it.copy(loadingVoiceId = null, error = "Couldn't play this voice message.") }
            } finally {
                _uiState.update { if (it.playingId == message.id) it.copy(playingId = null) else it }
            }
        }
    }

    fun onDeleteRequested(message: AdminChatMessage) = _uiState.update { it.copy(deleteTarget = message) }
    fun onDeleteCancelled() = _uiState.update { it.copy(deleteTarget = null) }

    fun onConfirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.update { s -> s.copy(deleteTarget = null, messages = s.messages.filterNot { it.id == target.id }) }
        viewModelScope.launch {
            try {
                repository.adminDeleteMessage(target.id)
                _uiState.update { it.copy(info = "Message deleted.") }
            } catch (e: Exception) {
                Napier.w("admin delete failed", e)
                _uiState.update { s -> s.copy(messages = (s.messages + target).sortedByDescending { it.createdAtMillis }, error = friendlyCommunityError(e, "Couldn't delete the message.")) }
            }
        }
    }

    override fun onCleared() {
        voicePlayer.stop()
    }

    fun onBack() = navigationManager.back()
}
