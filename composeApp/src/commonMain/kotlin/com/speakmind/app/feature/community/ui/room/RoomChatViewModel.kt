package com.speakmind.app.feature.community.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.ActiveRoomTracker
import com.speakmind.app.feature.community.data.ContentModerator
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.MessageKind
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.ReplyPreview
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.ReportReason
import com.speakmind.app.feature.community.data.repository.RoomConnection
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.feature.voice.platform.MicAccess
import com.speakmind.app.feature.voice.platform.VoicePlayer
import com.speakmind.app.feature.voice.platform.VoiceRecorder
import com.speakmind.app.navigation.CommunityAuthDestination
import com.speakmind.app.navigation.RoomDestination
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class RoomChatUiState(
    val roomId: String = "",
    val title: String = "",
    val kind: RoomKind = RoomKind.CHANNEL,
    val muted: Boolean = false,
    val otherUser: CommunityUser? = null,
    val isOtherOnline: Boolean = false,
    /** When the other person last read this private chat (epoch millis), for "Seen". */
    val otherLastReadAt: Long = 0L,
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    val typingText: String? = null,
    val messages: List<RoomMessage> = emptyList(),
    val inputText: String = "",
    val currentUserId: String = "",
    val isGuest: Boolean = false,
    val isBanned: Boolean = false,
    val isOffline: Boolean = false,
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val dailyLimitReached: Boolean = false,
    val contentWarning: String? = null,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val playingMessageId: String? = null,
    val loadingVoiceId: String? = null,
    val editingMessage: RoomMessage? = null,
    val replyingTo: RoomMessage? = null,
    /** Profile sheet for a tapped user. */
    val profileUser: CommunityUser? = null,
    val deleteTarget: RoomMessage? = null,
    val error: String? = null,
    val info: String? = null,
    val blockedUserIds: Set<String> = emptySet(),
    val onlineUserIds: Set<String> = emptySet(),
    /** Private chat only: either side has blocked the other. */
    val isDirectBlocked: Boolean = false,
    val reportTarget: ReportTarget? = null,
    val blockTarget: ReportTarget? = null,
) {
    /** Visitors who aren't signed in with Google can only read. */
    val guestRestricted: Boolean get() = isGuest
    val canPost: Boolean
        get() = !isBanned && !guestRestricted && !(kind == RoomKind.DIRECT && isDirectBlocked)
    val canSendVoice: Boolean get() = canPost && !isGuest
    val isOtherBlockedByMe: Boolean get() = otherUser?.uid in blockedUserIds

    /** Messages from people I blocked are hidden (except in our private chat, which shows a banner). */
    val visibleMessages: List<RoomMessage>
        get() = if (blockedUserIds.isEmpty() || kind == RoomKind.DIRECT) messages
        else messages.filterNot { it.senderId in blockedUserIds }

    /** Id of my newest sent message the other person has read (private chats only). */
    val seenMessageId: String?
        get() = if (kind != RoomKind.DIRECT || otherLastReadAt == 0L) null
        else messages.lastOrNull {
            it.senderId == currentUserId && !it.isPending && !it.isFailed && it.createdAtMillis <= otherLastReadAt
        }?.id
}

data class ReportTarget(
    val userId: String,
    val nickname: String,
    val message: RoomMessage? = null,
)

@OptIn(ExperimentalUuidApi::class)
class RoomChatViewModel(
    private val roomId: String,
    initialTitle: String,
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
    private val voiceRecorder: VoiceRecorder,
    private val voicePlayer: VoicePlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RoomChatUiState(
            roomId = roomId,
            title = initialTitle,
            currentUserId = repository.currentUserId() ?: "",
            isGuest = repository.isGuest(),
        )
    )
    val uiState: StateFlow<RoomChatUiState> = _uiState.asStateFlow()

    private var connection: RoomConnection? = null
    private var memberIds: Set<String> = emptySet()
    private var lastTypingSentAt = 0L
    private var typingStopJob: Job? = null
    private var recordingStartedAt = 0L
    private var recordingTicker: Job? = null
    private var markReadJob: Job? = null

    init {
        loadCached()
        loadRoom()
        loadInitialMessages()
        connectRealtime()
        observeOnline()
        observePlayback()
        observeBlocks()
        checkBan()
    }

    // ---------- Loading ----------

    private fun loadCached() {
        viewModelScope.launch {
            try {
                val cached = repository.cachedMessages(roomId, PAGE_SIZE)
                if (cached.isNotEmpty()) {
                    _uiState.update {
                        if (it.isInitialLoading) it.copy(messages = cached, isInitialLoading = false) else it
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadRoom() {
        viewModelScope.launch {
            try {
                val room = repository.getRoom(roomId) ?: return@launch
                _uiState.update {
                    it.copy(
                        title = room.displayName.ifEmpty { it.title },
                        kind = room.kind,
                        otherUser = room.otherUser,
                        memberCount = room.memberCount,
                        muted = room.muted,
                    )
                }
                if (room.kind != RoomKind.CHANNEL) {
                    val members = repository.roomMembers(roomId)
                    memberIds = members.map { it.user.uid }.toSet()
                    val other = room.otherUser?.uid
                    members.firstOrNull { it.user.uid == other }?.let { m ->
                        _uiState.update { it.copy(otherLastReadAt = m.lastReadAtMillis) }
                    }
                }
                refreshOnline(repository.onlineUserIds.value)
                if (room.kind == RoomKind.DIRECT) refreshDirectBlock()
            } catch (e: Exception) {
                Napier.w("loadRoom failed", e)
            }
        }
    }

    private fun loadInitialMessages() {
        viewModelScope.launch {
            try {
                val page = repository.loadMessages(roomId, PAGE_SIZE, before = null)
                _uiState.update { state ->
                    // The fresh page replaces cached rows (some may have been deleted elsewhere).
                    val pending = state.messages.filter { it.isPending || it.isFailed }
                    state.copy(
                        messages = mergeMessages(page, pending),
                        hasMore = page.size >= PAGE_SIZE,
                        isInitialLoading = false,
                        isOffline = false,
                    )
                }
                scheduleMarkRead()
            } catch (e: Exception) {
                Napier.w("loadInitialMessages failed", e)
                _uiState.update {
                    it.copy(isInitialLoading = false, isOffline = true, hasMore = false)
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isInitialLoading || state.isLoadingMore || !state.hasMore || state.isOffline) return
        val oldest = state.messages.firstOrNull { !it.isPending } ?: return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repository.loadMessages(roomId, PAGE_SIZE, before = oldest)
                _uiState.update {
                    it.copy(
                        messages = mergeMessages(page, it.messages),
                        hasMore = page.size >= PAGE_SIZE,
                        isLoadingMore = false,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onRetryConnection() {
        _uiState.update { it.copy(isOffline = false) }
        loadRoom()
        loadInitialMessages()
    }

    private fun checkBan() {
        viewModelScope.launch {
            if (repository.isBanned()) _uiState.update { it.copy(isBanned = true) }
        }
    }

    // ---------- Realtime ----------

    private fun connectRealtime() {
        viewModelScope.launch {
            val conn = repository.openRoomConnection(roomId)
            connection = conn
            launch {
                conn.newMessages.collect { incoming ->
                    val msg = withLocalReply(incoming)
                    _uiState.update { s ->
                        val existing = s.messages.firstOrNull { it.id == msg.id }
                        val keep = if (existing != null) msg.copy(replyTo = msg.replyTo ?: existing.replyTo, reactions = existing.reactions) else msg
                        s.copy(messages = mergeMessages(listOf(keep), s.messages))
                    }
                    if (msg.senderId != _uiState.value.currentUserId) scheduleMarkRead()
                }
            }
            launch {
                conn.editedMessages.collect { msg ->
                    _uiState.update { s ->
                        val existing = s.messages.firstOrNull { it.id == msg.id } ?: return@update s
                        // Realtime rows carry no reply/reaction details; keep the ones we have.
                        val merged = msg.copy(replyTo = msg.replyTo ?: existing.replyTo, reactions = existing.reactions)
                        s.copy(messages = mergeMessages(listOf(merged), s.messages))
                    }
                }
            }
            launch {
                conn.reactionChanges.collect { change -> applyReaction(change.messageId, change.userId, change.emoji, change.added) }
            }
            launch {
                conn.deletedMessageIds.collect { id ->
                    _uiState.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
                }
            }
            launch {
                conn.readMarkers.collect { (userId, readAt) ->
                    if (userId == _uiState.value.otherUser?.uid) {
                        _uiState.update { it.copy(otherLastReadAt = maxOf(it.otherLastReadAt, readAt)) }
                    }
                }
            }
            launch {
                conn.typingUsers.collect { typists ->
                    val others = typists.filter { it.userId != _uiState.value.currentUserId }
                    _uiState.update { it.copy(typingText = typingLabel(others.map { t -> t.nickname })) }
                }
            }
            try {
                conn.connect()
            } catch (e: Exception) {
                Napier.w("Room realtime connect failed", e)
            }
        }
    }

    private fun observeOnline() {
        viewModelScope.launch {
            repository.onlineUserIds.collect { refreshOnline(it) }
        }
    }

    private fun refreshOnline(online: Set<String>) {
        _uiState.update { it.copy(onlineUserIds = online) }
        _uiState.update { state ->
            when (state.kind) {
                RoomKind.DIRECT -> state.copy(isOtherOnline = state.otherUser?.uid in online)
                else -> state.copy(onlineCount = online.size)
            }
        }
    }

    private fun observeBlocks() {
        viewModelScope.launch {
            repository.refreshBlocks()
            repository.blockedUserIds.collect { ids -> _uiState.update { it.copy(blockedUserIds = ids) } }
        }
    }

    private suspend fun refreshDirectBlock() {
        val other = _uiState.value.otherUser ?: return
        val blocked = try { repository.isBlockedBetween(other.uid) } catch (_: Exception) { false }
        _uiState.update { it.copy(isDirectBlocked = blocked) }
    }

    private fun scheduleMarkRead() {
        if (_uiState.value.kind == RoomKind.CHANNEL) return
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            delay(800)
            repository.markRead(roomId)
        }
    }

    // ---------- Text ----------

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
        if (_uiState.value.editingMessage != null) return
        if (text.isBlank()) {
            sendTyping(false)
            return
        }
        val now = now()
        if (now - lastTypingSentAt > 3_000) {
            lastTypingSentAt = now
            sendTyping(true)
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(4_000)
            sendTyping(false)
        }
    }

    private fun sendTyping(isTyping: Boolean) {
        if (!isTyping) {
            typingStopJob?.cancel()
            if (lastTypingSentAt == 0L) return
            lastTypingSentAt = 0L
        }
        viewModelScope.launch { connection?.sendTyping(isTyping) }
    }

    fun onSendClicked() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty() || !state.canPost) return
        val moderation = ContentModerator.check(text)
        if (!moderation.isAllowed) {
            _uiState.update {
                it.copy(
                    inputText = "",
                    contentWarning = moderation.violationType?.userMessage
                        ?: "This message violates community guidelines.",
                )
            }
            return
        }
        state.editingMessage?.let { editing ->
            submitEdit(editing, text)
            return
        }
        viewModelScope.launch {
            if (!checkQuota()) return@launch
            sendTyping(false)
            val replyTo = _uiState.value.replyingTo
            val pending = pendingMessage(MessageKind.TEXT, text = text).copy(
                replyTo = replyTo?.let { ReplyPreview(it.id, it.senderNickname, it.text, it.kind == MessageKind.VOICE) },
            )
            _uiState.update { it.copy(inputText = "", replyingTo = null, messages = it.messages + pending) }
            sendPendingText(pending)
        }
    }

    private suspend fun sendPendingText(pending: RoomMessage) {
        try {
            val sent = repository.sendText(roomId, pending.id, pending.text, pending.replyTo?.messageId)
            replaceMessage(sent)
        } catch (e: Exception) {
            Napier.w("sendText failed", e)
            replaceMessage(pending.copy(isFailed = true))
            showServerError(e)
        }
    }

    private fun showServerError(e: Exception) {
        val msg = friendlyCommunityError(e, "Message not sent.")
        _uiState.update {
            it.copy(
                error = msg,
                isBanned = it.isBanned || "suspended" in msg,
                dailyLimitReached = it.dailyLimitReached || "today's" in msg,
            )
        }
        if (_uiState.value.kind == RoomKind.DIRECT) viewModelScope.launch { refreshDirectBlock() }
    }

    fun onRetry(messageId: String) {
        val msg = _uiState.value.messages.firstOrNull { it.id == messageId && it.isFailed } ?: return
        if (msg.kind != MessageKind.TEXT) {
            _uiState.update { s -> s.copy(messages = s.messages.filterNot { it.id == messageId }) }
            return
        }
        replaceMessage(msg.copy(isFailed = false))
        viewModelScope.launch { sendPendingText(msg.copy(isFailed = false)) }
    }

    /** The public Speaky Hub has a daily limit (also enforced on the server). */
    private suspend fun checkQuota(): Boolean {
        if (_uiState.value.kind != RoomKind.CHANNEL) return true
        val allowed = repository.checkAndIncrementDailyQuota()
        if (!allowed) _uiState.update { it.copy(dailyLimitReached = true) }
        return allowed
    }

    // ---------- Replies, reactions, profiles ----------

    fun onReplyRequested(message: RoomMessage) {
        if (!_uiState.value.canPost) return
        _uiState.update { it.copy(replyingTo = message, editingMessage = null) }
    }

    fun onCancelReply() = _uiState.update { it.copy(replyingTo = null) }

    fun onReactionClicked(message: RoomMessage, emoji: String) {
        val me = _uiState.value.currentUserId
        if (!_uiState.value.canPost || me.isEmpty() || message.isPending) return
        val adding = me !in message.reactions[emoji].orEmpty()
        applyReaction(message.id, me, emoji, adding)
        viewModelScope.launch {
            try {
                repository.toggleReaction(message, emoji)
            } catch (e: Exception) {
                Napier.w("toggleReaction failed", e)
                applyReaction(message.id, me, emoji, !adding)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't react.")) }
            }
        }
    }

    private fun applyReaction(messageId: String, userId: String, emoji: String, added: Boolean) {
        _uiState.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.id != messageId) return@map m
                val users = m.reactions[emoji].orEmpty()
                val updated = if (added) (users + userId).distinct() else users - userId
                m.copy(reactions = (m.reactions - emoji).let { if (updated.isEmpty()) it else it + (emoji to updated) })
            })
        }
    }

    /** Fills in the quoted message for realtime rows, which don't include it. */
    private fun withLocalReply(msg: RoomMessage): RoomMessage {
        val replyId = msg.replyTo?.messageId ?: return msg
        if (msg.replyTo.senderNickname.isNotEmpty()) return msg
        val original = _uiState.value.messages.firstOrNull { it.id == replyId } ?: return msg
        return msg.copy(replyTo = ReplyPreview(original.id, original.senderNickname, original.text, original.kind == MessageKind.VOICE))
    }

    fun onUserClicked(userId: String) {
        if (userId.isEmpty()) return
        val known = _uiState.value.messages.firstOrNull { it.senderId == userId }
        // Show what we know immediately, then refresh with the full profile.
        if (known != null) {
            _uiState.update {
                it.copy(profileUser = CommunityUser(userId, known.senderNickname, known.senderGender, 0L, known.senderPhotoUrl))
            }
        }
        viewModelScope.launch {
            repository.getUser(userId)?.let { user -> _uiState.update { it.copy(profileUser = user) } }
        }
    }

    fun onProfileDismissed() = _uiState.update { it.copy(profileUser = null) }

    // ---------- Edit & delete ----------

    fun onEditRequested(message: RoomMessage) {
        if (message.kind != MessageKind.TEXT || message.senderId != _uiState.value.currentUserId) return
        if (now() - message.createdAtMillis > EDIT_WINDOW_MS) {
            _uiState.update { it.copy(error = "Messages can only be edited for 24 hours.") }
            return
        }
        _uiState.update { it.copy(editingMessage = message, replyingTo = null, inputText = message.text) }
    }

    fun onCancelEdit() = _uiState.update { it.copy(editingMessage = null, inputText = "") }

    private fun submitEdit(original: RoomMessage, newText: String) {
        _uiState.update { it.copy(editingMessage = null, inputText = "") }
        if (newText == original.text) return
        replaceMessage(original.copy(text = newText, isEdited = true))
        viewModelScope.launch {
            try {
                replaceMessage(repository.editMessage(original, newText))
            } catch (e: Exception) {
                Napier.w("editMessage failed", e)
                replaceMessage(original)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't edit the message.")) }
            }
        }
    }

    fun onDeleteRequested(message: RoomMessage) = _uiState.update { it.copy(deleteTarget = message) }
    fun onDeleteDismissed() = _uiState.update { it.copy(deleteTarget = null) }

    fun onConfirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.update { s -> s.copy(deleteTarget = null, messages = s.messages.filterNot { it.id == target.id }) }
        if (target.isPending || target.isFailed) return
        viewModelScope.launch {
            try {
                repository.deleteMessage(target)
            } catch (e: Exception) {
                Napier.w("deleteMessage failed", e)
                replaceMessage(target)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't delete the message.")) }
            }
        }
    }

    // ---------- Voice ----------

    fun onMicPressed() {
        val state = _uiState.value
        if (!state.canSendVoice || state.isRecording) {
            if (state.isGuest) _uiState.update { it.copy(error = "Sign in with Google to send voice messages.") }
            return
        }
        if (!MicAccess.isGranted()) {
            MicAccess.request()
            return
        }
        try {
            voicePlayer.stop()
            voiceRecorder.startRecording()
        } catch (e: Exception) {
            Napier.w("startRecording failed", e)
            _uiState.update { it.copy(error = "Couldn't start the microphone.") }
            return
        }
        recordingStartedAt = now()
        _uiState.update { it.copy(isRecording = true, recordingElapsedMs = 0L, playingMessageId = null) }
        recordingTicker = viewModelScope.launch {
            while (true) {
                delay(200)
                val elapsed = now() - recordingStartedAt
                _uiState.update { it.copy(recordingElapsedMs = elapsed) }
                if (elapsed >= MAX_VOICE_MS) {
                    onSendRecording()
                    break
                }
            }
        }
    }

    fun onCancelRecording() {
        recordingTicker?.cancel()
        voiceRecorder.cancelRecording()
        _uiState.update { it.copy(isRecording = false, recordingElapsedMs = 0L) }
    }

    fun onSendRecording() {
        if (!_uiState.value.isRecording) return
        recordingTicker?.cancel()
        val duration = now() - recordingStartedAt
        val audio = voiceRecorder.stopRecording()
        _uiState.update { it.copy(isRecording = false, recordingElapsedMs = 0L) }
        if (duration < MIN_VOICE_MS || audio.isEmpty()) return
        viewModelScope.launch {
            if (!checkQuota()) return@launch
            val pending = pendingMessage(MessageKind.VOICE, durationMs = duration)
            _uiState.update { it.copy(messages = it.messages + pending) }
            try {
                val sent = repository.sendVoice(roomId, pending.id, audio, duration)
                replaceMessage(sent)
            } catch (e: Exception) {
                Napier.w("sendVoice failed", e)
                replaceMessage(pending.copy(isFailed = true))
                showServerError(e)
            }
        }
    }

    fun onPlayVoice(message: RoomMessage) {
        val path = message.voicePath ?: return
        if (_uiState.value.playingMessageId == message.id) {
            voicePlayer.stop()
            _uiState.update { it.copy(playingMessageId = null) }
            return
        }
        voicePlayer.stop()
        _uiState.update { it.copy(loadingVoiceId = message.id, playingMessageId = null) }
        viewModelScope.launch {
            try {
                // Clips are private: download with the user's session, then play locally.
                val bytes = repository.downloadVoice(path)
                _uiState.update { it.copy(loadingVoiceId = null, playingMessageId = message.id) }
                voicePlayer.playFromBytes(bytes)
            } catch (e: Exception) {
                Napier.w("Voice playback failed", e)
                _uiState.update {
                    it.copy(loadingVoiceId = null, playingMessageId = null, error = "Couldn't play this voice message.")
                }
            }
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            var wasPlaying = false
            voicePlayer.isPlaying.collect { playing ->
                if (wasPlaying && !playing) _uiState.update { it.copy(playingMessageId = null) }
                wasPlaying = playing
            }
        }
    }

    // ---------- Private messages ----------

    fun onSignInRequested() = navigationManager.navigate(CommunityAuthDestination)

    /** Opens (or starts) a private chat with someone from Speaky Hub. */
    fun onMessageUser(userId: String, nickname: String) {
        if (userId == _uiState.value.currentUserId || userId.isEmpty()) return
        if (_uiState.value.isGuest) {
            onSignInRequested()
            return
        }
        viewModelScope.launch {
            try {
                val dm = repository.openDirectChat(userId)
                navigationManager.navigate(RoomDestination(dm, nickname))
            } catch (e: Exception) {
                Napier.w("openDirectChat failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't open the chat.")) }
            }
        }
    }

    fun onToggleMute() {
        val muted = !_uiState.value.muted
        _uiState.update { it.copy(muted = muted) }
        viewModelScope.launch {
            try {
                repository.setMuted(roomId, muted)
                _uiState.update { it.copy(info = if (muted) "Notifications muted." else "Notifications on.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(muted = !muted, error = friendlyCommunityError(e, "Couldn't change notifications.")) }
            }
        }
    }

    // ---------- Report & block ----------

    fun onReportRequested(target: ReportTarget) = _uiState.update { it.copy(reportTarget = target) }
    fun onReportDismissed() = _uiState.update { it.copy(reportTarget = null) }

    fun onSubmitReport(reason: ReportReason, details: String, alsoBlock: Boolean) {
        val target = _uiState.value.reportTarget ?: return
        _uiState.update { it.copy(reportTarget = null) }
        viewModelScope.launch {
            try {
                repository.reportUser(target.userId, reason, details, target.message)
                if (alsoBlock) block(target.userId)
                _uiState.update {
                    it.copy(info = "Thanks — we'll review this report." + if (alsoBlock) " ${target.nickname} is blocked." else "")
                }
            } catch (e: Exception) {
                Napier.w("report failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't send the report. Try again.")) }
            }
        }
    }

    fun onBlockRequested(target: ReportTarget) = _uiState.update { it.copy(blockTarget = target) }
    fun onBlockDismissed() = _uiState.update { it.copy(blockTarget = null) }

    fun onConfirmBlock() {
        val target = _uiState.value.blockTarget ?: return
        _uiState.update { it.copy(blockTarget = null) }
        viewModelScope.launch {
            try {
                block(target.userId)
                _uiState.update { it.copy(info = "${target.nickname} is blocked. They can't message you privately.") }
            } catch (e: Exception) {
                Napier.w("block failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't block this user.")) }
            }
        }
    }

    private suspend fun block(userId: String) {
        repository.blockUser(userId)
        if (_uiState.value.kind == RoomKind.DIRECT) refreshDirectBlock()
    }

    fun onUnblockUser(userId: String, nickname: String) {
        viewModelScope.launch {
            try {
                repository.unblockUser(userId)
                if (_uiState.value.otherUser?.uid == userId) refreshDirectBlock()
                _uiState.update { it.copy(info = "$nickname is unblocked.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "Couldn't unblock this user.") }
            }
        }
    }

    fun onUnblockOther() {
        val other = _uiState.value.otherUser ?: return
        viewModelScope.launch {
            try {
                repository.unblockUser(other.uid)
                refreshDirectBlock()
                _uiState.update { it.copy(info = "${other.nickname} is unblocked.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "Couldn't unblock this user.") }
            }
        }
    }

    // ---------- Misc ----------

    fun onInfoDismissed() = _uiState.update { it.copy(info = null) }
    fun onDailyLimitDismissed() = _uiState.update { it.copy(dailyLimitReached = false) }
    fun onContentWarningDismissed() = _uiState.update { it.copy(contentWarning = null) }
    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
    fun onBack() = navigationManager.back()

    /** Screen visibility drives the user's online status and notification suppression. */
    fun onVisibilityChanged(visible: Boolean) {
        repository.setActive(visible)
        if (visible) {
            ActiveRoomTracker.activeRoomId.value = roomId
            scheduleMarkRead()
        } else if (ActiveRoomTracker.activeRoomId.value == roomId) {
            ActiveRoomTracker.activeRoomId.value = null
        }
    }

    private fun pendingMessage(kind: MessageKind, text: String = "", durationMs: Long = 0L): RoomMessage {
        val now = now()
        return RoomMessage(
            id = Uuid.random().toString(),
            roomId = roomId,
            senderId = _uiState.value.currentUserId,
            senderNickname = "",
            senderGender = "",
            senderPhotoUrl = null,
            kind = kind,
            text = text,
            voicePath = null,
            voiceDurationMs = durationMs,
            createdAtMillis = now,
            createdAtRaw = "",
            isPending = true,
        )
    }

    private fun replaceMessage(msg: RoomMessage) {
        _uiState.update { it.copy(messages = mergeMessages(listOf(msg), it.messages)) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        recordingTicker?.cancel()
        if (_uiState.value.isRecording) voiceRecorder.cancelRecording()
        voicePlayer.stop()
        if (ActiveRoomTracker.activeRoomId.value == roomId) ActiveRoomTracker.activeRoomId.value = null
        val conn = connection
        val wasTyping = lastTypingSentAt != 0L
        // viewModelScope is already cancelled here; finish cleanup outside it.
        GlobalScope.launch(NonCancellable) {
            if (wasTyping) conn?.sendTyping(false)
            conn?.close()
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val MIN_VOICE_MS = 700L
        private const val MAX_VOICE_MS = 120_000L
        private const val EDIT_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}

/** Merges [incoming] into [current] by id (incoming wins), sorted oldest first; pending messages stay last. */
internal fun mergeMessages(incoming: List<RoomMessage>, current: List<RoomMessage>): List<RoomMessage> {
    val byId = LinkedHashMap<String, RoomMessage>()
    current.forEach { byId[it.id] = it }
    incoming.forEach { msg ->
        val existing = byId[msg.id]
        // A realtime echo has no sender info for our own message; keep what we have.
        byId[msg.id] = if (existing != null && msg.senderNickname.isEmpty() && existing.senderNickname.isNotEmpty()) {
            msg.copy(
                senderNickname = existing.senderNickname,
                senderGender = existing.senderGender,
                senderPhotoUrl = existing.senderPhotoUrl,
            )
        } else msg
    }
    return byId.values.sortedWith(compareBy<RoomMessage> { it.isPending || it.isFailed }.thenBy { it.createdAtMillis })
}

private fun typingLabel(names: List<String>): String? = when {
    names.isEmpty() -> null
    names.size == 1 -> "${names[0].ifEmpty { "Someone" }} is typing…"
    names.size == 2 -> "${names[0]} and ${names[1]} are typing…"
    else -> "${names.size} people are typing…"
}

private fun now() = Clock.System.now().toEpochMilliseconds()
