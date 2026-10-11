package com.speakmind.app.feature.community.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.ContentModerator
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.MessageKind
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.ReportReason
import com.speakmind.app.feature.community.data.repository.RoomConnection
import com.speakmind.app.feature.voice.platform.MicAccess
import com.speakmind.app.feature.voice.platform.VoicePlayer
import com.speakmind.app.feature.voice.platform.VoiceRecorder
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
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
    val isMember: Boolean = true,
    val isJoining: Boolean = false,
    val otherUser: CommunityUser? = null,
    val isOtherOnline: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    val typingText: String? = null,
    val messages: List<RoomMessage> = emptyList(),
    val inputText: String = "",
    val currentUserId: String = "",
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val dailyLimitReached: Boolean = false,
    val contentWarning: String? = null,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val playingMessageId: String? = null,
    val error: String? = null,
    val info: String? = null,
    val blockedUserIds: Set<String> = emptySet(),
    /** Private chat only: either side has blocked the other. */
    val isDirectBlocked: Boolean = false,
    val reportTarget: ReportTarget? = null,
    val blockTarget: ReportTarget? = null,
) {
    val canPost: Boolean get() = (kind == RoomKind.CHANNEL || isMember) && !(kind == RoomKind.DIRECT && isDirectBlocked)
    val isOtherBlockedByMe: Boolean get() = otherUser?.uid in blockedUserIds

    /** Messages from people I blocked are hidden (except in our private chat, which shows a banner). */
    val visibleMessages: List<RoomMessage>
        get() = if (blockedUserIds.isEmpty() || kind == RoomKind.DIRECT) messages
        else messages.filterNot { it.senderId in blockedUserIds }
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
        RoomChatUiState(roomId = roomId, title = initialTitle, currentUserId = repository.currentUserId() ?: "")
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
        loadRoom()
        loadInitialMessages()
        connectRealtime()
        observeOnline()
        observePlayback()
        observeBlocks()
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

    // ---------- Loading ----------

    private fun loadRoom() {
        viewModelScope.launch {
            try {
                val room = repository.getRoom(roomId) ?: return@launch
                memberIds = if (room.kind == RoomKind.CHANNEL) emptySet()
                else repository.roomMemberIds(roomId).toSet()
                _uiState.update {
                    it.copy(
                        title = room.displayName.ifEmpty { it.title },
                        kind = room.kind,
                        isMember = room.kind == RoomKind.CHANNEL || room.isMember,
                        otherUser = room.otherUser,
                        memberCount = room.memberCount,
                    )
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
                    state.copy(
                        messages = mergeMessages(page, state.messages),
                        hasMore = page.size >= PAGE_SIZE,
                        isInitialLoading = false,
                    )
                }
                scheduleMarkRead()
            } catch (e: Exception) {
                Napier.w("loadInitialMessages failed", e)
                _uiState.update { it.copy(isInitialLoading = false, error = "Couldn't load messages. Check your connection.") }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isInitialLoading || state.isLoadingMore || !state.hasMore) return
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

    // ---------- Realtime ----------

    private fun connectRealtime() {
        viewModelScope.launch {
            val conn = repository.openRoomConnection(roomId)
            connection = conn
            launch {
                conn.newMessages.collect { msg ->
                    _uiState.update { it.copy(messages = mergeMessages(listOf(msg), it.messages)) }
                    if (msg.senderId != _uiState.value.currentUserId) scheduleMarkRead()
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
        _uiState.update { state ->
            when (state.kind) {
                RoomKind.CHANNEL -> state.copy(onlineCount = online.size)
                RoomKind.GROUP -> state.copy(onlineCount = memberIds.count { it in online })
                RoomKind.DIRECT -> state.copy(isOtherOnline = state.otherUser?.uid in online)
            }
        }
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
        viewModelScope.launch {
            if (!checkQuota()) return@launch
            sendTyping(false)
            val pending = pendingMessage(MessageKind.TEXT, text = text)
            _uiState.update { it.copy(inputText = "", messages = it.messages + pending) }
            sendPendingText(pending)
        }
    }

    private suspend fun sendPendingText(pending: RoomMessage) {
        try {
            val sent = repository.sendText(roomId, pending.id, pending.text)
            replaceMessage(sent)
        } catch (e: Exception) {
            Napier.w("sendText failed", e)
            replaceMessage(pending.copy(isFailed = true))
        }
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

    /** Only the public Speaky Hub has a daily limit. */
    private suspend fun checkQuota(): Boolean {
        if (_uiState.value.kind != RoomKind.CHANNEL) return true
        val allowed = repository.checkAndIncrementDailyQuota()
        if (!allowed) _uiState.update { it.copy(dailyLimitReached = true) }
        return allowed
    }

    // ---------- Voice ----------

    fun onMicPressed() {
        if (!_uiState.value.canPost || _uiState.value.isRecording) return
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
            }
        }
    }

    fun onPlayVoice(message: RoomMessage) {
        val url = message.voiceUrl ?: return
        if (_uiState.value.playingMessageId == message.id) {
            voicePlayer.stop()
            _uiState.update { it.copy(playingMessageId = null) }
            return
        }
        _uiState.update { it.copy(playingMessageId = message.id) }
        viewModelScope.launch {
            try {
                voicePlayer.playFromUrl(url)
            } catch (e: Exception) {
                Napier.w("Voice playback failed", e)
                _uiState.update { it.copy(playingMessageId = null) }
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

    // ---------- Groups ----------

    fun onJoinGroup() {
        if (_uiState.value.isJoining) return
        _uiState.update { it.copy(isJoining = true) }
        viewModelScope.launch {
            try {
                repository.joinGroup(roomId)
                repository.currentUserId()?.let { memberIds = memberIds + it }
                _uiState.update { it.copy(isMember = true, isJoining = false, memberCount = it.memberCount + 1) }
                refreshOnline(repository.onlineUserIds.value)
            } catch (e: Exception) {
                Napier.w("joinGroup failed", e)
                _uiState.update { it.copy(isJoining = false, error = "Couldn't join the group.") }
            }
        }
    }

    fun onLeaveGroup() {
        viewModelScope.launch {
            try {
                repository.leaveRoom(roomId)
                navigationManager.back()
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "Couldn't leave the group.") }
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
                _uiState.update { it.copy(error = "Couldn't send the report. Try again.") }
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
                _uiState.update { it.copy(error = "Couldn't block this user.") }
            }
        }
    }

    private suspend fun block(userId: String) {
        repository.blockUser(userId)
        if (_uiState.value.kind == RoomKind.DIRECT) refreshDirectBlock()
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

    /** Screen visibility drives the user's online status. */
    fun onVisibilityChanged(visible: Boolean) = repository.setActive(visible)

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
            voiceUrl = null,
            voiceDurationMs = durationMs,
            createdAtMillis = now,
            createdAtRaw = "",
            isPending = true,
        )
    }

    private fun replaceMessage(msg: RoomMessage) {
        _uiState.update { it.copy(messages = mergeMessages(listOf(msg), it.messages)) }
    }

    override fun onCleared() {
        super.onCleared()
        recordingTicker?.cancel()
        if (_uiState.value.isRecording) voiceRecorder.cancelRecording()
        voicePlayer.stop()
        val conn = connection
        val wasTyping = lastTypingSentAt != 0L
        // viewModelScope is already cancelled here; finish cleanup outside it.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(NonCancellable) {
            if (wasTyping) conn?.sendTyping(false)
            conn?.close()
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val MIN_VOICE_MS = 700L
        private const val MAX_VOICE_MS = 120_000L
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
