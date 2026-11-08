package com.speakmind.app.feature.community.ui.room

import com.speakmind.app.premium.Paywall
import com.speakmind.app.feature.community.data.repository.AiCorrection
import com.speakmind.app.feature.community.data.repository.BlockedException
import com.speakmind.app.premium.Premium
import com.speakmind.app.feature.community.ui.components.freeMessagesPaywall
import com.speakmind.app.feature.community.ui.components.openPrivateChat
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
import com.speakmind.app.feature.community.ui.auth.GoogleIdResult
import com.speakmind.app.feature.community.ui.auth.SignInOutcome
import com.speakmind.app.feature.community.ui.auth.friendlySignInError
import com.speakmind.app.feature.community.ui.auth.signInWithGoogleInPlace
import com.speakmind.app.navigation.CommunitySetupDestination
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val currentUserId: String = "",
    val isGuest: Boolean = false,
    /** Admins can remove someone's photo from their profile sheet. */
    val isAdmin: Boolean = false,
    /** Admin: who is about to be banned. */
    val adminBanTarget: CommunityUser? = null,
    val isSigningIn: Boolean = false,
    val isBanned: Boolean = false,
    val isOffline: Boolean = false,
    val isInitialLoading: Boolean = true,
    /** False until the fresh first page replaced the saved copy: no delete/slide animations before that. */
    val liveAnimations: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val dailyLimitReached: Boolean = false,
    val contentWarning: String? = null,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    /** The voice message loaded in the player (playing or paused). */
    val playingMessageId: String? = null,
    val loadingVoiceId: String? = null,
    val isVoicePaused: Boolean = false,
    val playbackProgress: Float = 0f,
    val playbackPositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    /** Recent input loudness (0..1) for the live waveform while recording. */
    val recordingLevels: List<Float> = emptyList(),
    /** False until the saved login has been restored; nothing guest-only is shown before that. */
    val authReady: Boolean = false,
    val editingMessage: RoomMessage? = null,
    val replyingTo: RoomMessage? = null,
    /** Profile sheet for a tapped user. */
    val profileUser: CommunityUser? = null,
    val deleteTarget: RoomMessage? = null,
    val error: String? = null,
    val info: String? = null,
    /** AI is fixing / translating the message being typed. */
    val isCorrecting: Boolean = false,
    /** What the user typed before the AI fixed it, for Undo; null when nothing was fixed. */
    val correctionOriginal: String? = null,
    val blockedUserIds: Set<String> = emptySet(),
    val onlineUserIds: Set<String> = emptySet(),
    /** Private chat only: either side has blocked the other. */
    val isDirectBlocked: Boolean = false,
    val reportTarget: ReportTarget? = null,
    val confirmDeleteChat: Boolean = false,
    val blockTarget: ReportTarget? = null,
) {
    /** Visitors who aren't signed in with Google can only read. */
    val guestRestricted: Boolean get() = authReady && isGuest
    val canPost: Boolean
        get() = authReady && !isBanned && !guestRestricted && !(kind == RoomKind.DIRECT && isDirectBlocked)
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

/** Opens a private chat with [userId]; the chat itself is created only with the first message. */
fun draftDirectRoomId(userId: String) = DRAFT_DM_PREFIX + userId

private const val DRAFT_DM_PREFIX = "dm:"

data class ReportTarget(
    val userId: String,
    val nickname: String,
    val message: RoomMessage? = null,
)

@OptIn(ExperimentalUuidApi::class)
class RoomChatViewModel(
    initialRoomId: String,
    initialTitle: String,
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
    private val voiceRecorder: VoiceRecorder,
    private val voicePlayer: VoicePlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RoomChatUiState(
            roomId = initialRoomId,
            title = initialTitle,
            currentUserId = repository.currentUserId() ?: "",
            isGuest = repository.isGuest(),
        )
    )
    val uiState: StateFlow<RoomChatUiState> = _uiState.asStateFlow()

    /** The message being typed: its own flow, so a keystroke doesn't redraw the whole chat. */
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private var roomId: String = initialRoomId

    /** Set while this is a private chat that doesn't exist yet (nothing has been sent). */
    private var draftOtherUserId: String? = initialRoomId.removePrefix(DRAFT_DM_PREFIX).takeIf { initialRoomId.startsWith(DRAFT_DM_PREFIX) }
    private val createRoomLock = Mutex()

    private var connection: RoomConnection? = null
    private var memberIds: Set<String> = emptySet()
    private var lastTypingSentAt = 0L
    private var typingStopJob: Job? = null
    private var recordingStartedAt = 0L
    private var recordingTicker: Job? = null
    private var markReadJob: Job? = null


    init {
        loadCached()
        viewModelScope.launch {
            // Opened from a notification on a cold start, this screen can be created before the saved
            // login is restored: it would look signed out and load without the user's session.
            repository.hasSession()
            _uiState.update {
                it.copy(currentUserId = repository.currentUserId() ?: "", isGuest = repository.isGuest(), authReady = true)
            }
            val draftUser = draftOtherUserId
            if (draftUser != null) openDraft(draftUser) else startSession()
        }
    }

    /** Uses our existing chat with this person, or shows an empty one that isn't saved until the first message. */
    private suspend fun openDraft(otherUserId: String) {
        val existing = try { repository.findDirectChat(otherUserId) } catch (e: Exception) {
            Napier.w("findDirectChat failed", e)
            null
        }
        if (existing != null) {
            useRoom(existing)
            loadCached()
            startSession()
            return
        }
        val other = try { repository.getUser(otherUserId) } catch (_: Exception) { null }
        _uiState.update {
            it.copy(
                kind = RoomKind.DIRECT,
                otherUser = other,
                title = other?.nickname?.ifEmpty { null } ?: it.title,
                isInitialLoading = false,
                hasMore = false,
            )
        }
        observeOnline()
        observePlayback()
        observeBlocks()
        checkBan()
        refreshDirectBlock()
    }

    private fun useRoom(id: String) {
        val old = roomId
        roomId = id
        draftOtherUserId = null
        _uiState.update { s -> s.copy(roomId = id, messages = s.messages.map { if (it.roomId == old) it.copy(roomId = id) else it }) }
        if (ActiveRoomTracker.activeRoomId.value == old) ActiveRoomTracker.activeRoomId.value = id
    }

    /** A draft private chat becomes a real one right before its first message is sent. */
    private suspend fun ensureRoomCreated(): Boolean = createRoomLock.withLock {
        val otherUserId = draftOtherUserId ?: return@withLock true
        try {
            useRoom(repository.openDirectChat(otherUserId))
            loadRoom()
            connectRealtime()
            true
        } catch (e: BlockedException) {
            val name = _uiState.value.otherUser?.nickname ?: "this person"
            _uiState.update { it.copy(error = "You can't message $name.", isDirectBlocked = true) }
            false
        } catch (e: Exception) {
            Napier.w("openDirectChat failed", e)
            _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't open the chat.")) }
            false
        }
    }

    private fun startSession() {
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
            val admin = repository.isAdmin()
            _uiState.update { it.copy(isAdmin = admin) }
        }
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
                // Messages deleted elsewhere (e.g. a banned user's) vanish without a delete animation.
                viewModelScope.launch {
                    kotlinx.coroutines.delay(700)
                    _uiState.update { it.copy(liveAnimations = true) }
                }
                scheduleMarkRead()
            } catch (e: Exception) {
                Napier.w("loadInitialMessages failed", e)
                _uiState.update {
                    it.copy(isInitialLoading = false, isOffline = true, hasMore = false, liveAnimations = true)
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

    /** Also for Speaky Hub: reading it re-arms its "one notification until you open the Hub" alert. */
    private fun scheduleMarkRead() {
        if (draftOtherUserId != null) return
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            delay(800)
            repository.markRead(roomId)
        }
    }

    // ---------- Text ----------

    fun onInputChanged(text: String) {
        // Editing by hand after an AI fix: Undo would bring back old text over the new, so drop it.
        _input.value = text
        if (_uiState.value.correctionOriginal != null) _uiState.update { it.copy(correctionOriginal = null) }
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
        val typed = _input.value
        val text = typed.trim()
        if (text.isEmpty() || !state.canPost) return
        val moderation = ContentModerator.check(text)
        if (!moderation.isAllowed) {
            _uiState.update {
                // Keep the text so the user can fix it instead of retyping.
                it.copy(
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
        // Shown at once; the quota and first-message checks run after, and undo it if they say no.
        val replyTo = state.replyingTo
        val pending = pendingMessage(MessageKind.TEXT, text = text).copy(
            replyTo = replyTo?.let { ReplyPreview(it.id, it.senderNickname, it.text, it.kind == MessageKind.VOICE) },
        )
        _input.value = ""
        _uiState.update { it.copy(replyingTo = null, correctionOriginal = null, messages = it.messages + pending) }
        sendTyping(false)
        viewModelScope.launch {
            if (!checkQuota() || !ensureRoomCreated()) {
                _uiState.update { s -> s.copy(messages = s.messages.filterNot { it.id == pending.id }, replyingTo = replyTo) }
                if (_input.value.isEmpty()) _input.value = typed
                return@launch
            }
            sendPendingText(pending.copy(roomId = roomId))
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
        if (_uiState.value.kind == RoomKind.DIRECT) return useFreePrivateMessage()
        if (_uiState.value.kind != RoomKind.CHANNEL) return true
        val allowed = repository.checkAndIncrementDailyQuota()
        if (!allowed) _uiState.update { it.copy(dailyLimitReached = true) }
        return allowed
    }

    /** ✨: AI fixes the English, or translates the message to English; Undo restores what was typed. */
    fun onFixClicked() {
        val state = _uiState.value
        val text = _input.value.trim()
        if (text.isEmpty() || state.isCorrecting) return
        _uiState.update { it.copy(isCorrecting = true) }
        viewModelScope.launch {
            val result = repository.correctMessage(text, Premium.active.value)
            _uiState.update { it.copy(isCorrecting = false) }
            when (result) {
                // Typed more meanwhile: keep that instead of overwriting it.
                is AiCorrection.Fixed -> if (_input.value.trim() == text) {
                    _input.value = result.text
                    _uiState.update {
                        it.copy(
                            correctionOriginal = if (result.text == text) null else text,
                            info = if (result.text == text) "✨ Looks good, no changes needed" else null,
                        )
                    }
                }
                is AiCorrection.LimitReached -> Premium.showPaywall(
                    Paywall(
                        title = "Your free AI corrections are used up",
                        message = "You get ${result.limit} free AI corrections every 24 hours. Subscribe to fix and " +
                            "translate all your messages, or come back when the timer ends.",
                        waitMs = result.waitMs,
                        freeThing = "free corrections",
                        emoji = "✨",
                        onUnlocked = ::onFixClicked,
                    ),
                )
                is AiCorrection.TooLong -> _uiState.update {
                    it.copy(info = "AI can fix up to ${result.maxChars} characters. Please shorten your message.")
                }
                // Main and fallback AI both out of quota or busy: offer Premium straight away, even on a first try.
                AiCorrection.OutOfQuota -> if (Premium.active.value) {
                    _uiState.update { it.copy(info = "AI is busy right now. Please try again in a moment.") }
                } else {
                    Premium.showPaywall(
                        Paywall(
                            title = "AI corrections",
                            message = "Free AI corrections are busy right now. Subscribe to Speaky Premium to fix " +
                                "and translate your messages any time.",
                            emoji = "✨",
                            onUnlocked = ::onFixClicked,
                        ),
                    )
                }
                AiCorrection.Failed -> _uiState.update {
                    it.copy(info = "AI is busy right now. Please try again in a moment.")
                }
            }
        }
    }

    fun onUndoFix() {
        val original = _uiState.value.correctionOriginal ?: return
        _input.value = original
        _uiState.update { it.copy(correctionOriginal = null) }
    }

    /** Free users: one of their daily free private messages, or the subscribe screen when none are left. */
    private suspend fun useFreePrivateMessage(): Boolean {
        if (Premium.active.value) return true
        // Offline or unknown: let it through rather than block a paying-to-be user on a network hiccup.
        val free = repository.freePrivateMessages() ?: return true
        if (free.left <= 0) {
            Premium.showPaywall(freeMessagesPaywall("Your free messages are used up", free) {})
            return false
        }
        return true
    }

    // ---------- Replies, reactions, profiles ----------

    /** A quoted message was tapped but it was deleted, or is older than the history we can load. */
    fun onReplyNotFound() = _uiState.update { it.copy(info = "The original message is no longer available.") }

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
        _input.value = message.text
        _uiState.update { it.copy(editingMessage = message, replyingTo = null) }
    }

    fun onCancelEdit() {
        _input.value = ""
        _uiState.update { it.copy(editingMessage = null) }
    }

    private fun submitEdit(original: RoomMessage, newText: String) {
        _input.value = ""
        _uiState.update { it.copy(editingMessage = null) }
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
                if (target.senderId == _uiState.value.currentUserId) repository.deleteMessage(target)
                else repository.adminDeleteMessage(target.id)
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
        _uiState.update {
            it.copy(isRecording = true, recordingElapsedMs = 0L, playingMessageId = null, recordingLevels = emptyList())
        }
        recordingTicker = viewModelScope.launch {
            launch {
                voiceRecorder.amplitudes.collect { level ->
                    _uiState.update { s ->
                        if (!s.isRecording) s else s.copy(recordingLevels = (s.recordingLevels + level).takeLast(LIVE_BARS))
                    }
                }
            }
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
        val waveform = voiceRecorder.recordedLevels(WAVEFORM_BARS)
        _uiState.update { it.copy(isRecording = false, recordingElapsedMs = 0L) }
        if (duration < MIN_VOICE_MS || audio.isEmpty()) return
        viewModelScope.launch {
            if (!checkQuota() || !ensureRoomCreated()) return@launch
            val pending = pendingMessage(MessageKind.VOICE, durationMs = duration).copy(voiceWaveform = waveform)
            _uiState.update { it.copy(messages = it.messages + pending) }
            try {
                val sent = repository.sendVoice(roomId, pending.id, audio, duration, waveform)
                replaceMessage(sent)
            } catch (e: Exception) {
                Napier.w("sendVoice failed", e)
                replaceMessage(pending.copy(isFailed = true))
                showServerError(e)
            }
        }
    }

    /** Play, pause or resume a voice message. */
    fun onPlayVoice(message: RoomMessage) {
        val state = _uiState.value
        if (state.playingMessageId == message.id) {
            if (state.isVoicePaused) voicePlayer.resume() else voicePlayer.pause()
            return
        }
        startVoice(message, startAt = 0f)
    }

    /** Tap or drag on a waveform: jump there (starting that clip if needed). */
    fun onSeekVoice(message: RoomMessage, fraction: Float) {
        if (_uiState.value.playingMessageId == message.id) {
            voicePlayer.seekTo(fraction)
            _uiState.update { it.copy(playbackProgress = fraction.coerceIn(0f, 1f)) }
        } else {
            startVoice(message, startAt = fraction)
        }
    }

    /** 1× → 1.5× → 2× → 1×. */
    fun onCycleVoiceSpeed() {
        val next = when (_uiState.value.playbackSpeed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        voicePlayer.setSpeed(next)
        _uiState.update { it.copy(playbackSpeed = next) }
    }

    private fun startVoice(message: RoomMessage, startAt: Float) {
        val path = message.voicePath ?: return
        voicePlayer.stop()
        _uiState.update {
            it.copy(loadingVoiceId = message.id, playingMessageId = null, playbackProgress = 0f, playbackPositionMs = 0L)
        }
        viewModelScope.launch {
            try {
                // Clips are private: download with the user's session, then play locally.
                val bytes = repository.downloadVoice(path)
                if (_uiState.value.loadingVoiceId != message.id) return@launch // another clip was tapped meanwhile
                _uiState.update {
                    it.copy(loadingVoiceId = null, playingMessageId = message.id, isVoicePaused = false, playbackProgress = startAt)
                }
                voicePlayer.setSpeed(_uiState.value.playbackSpeed)
                voicePlayer.playFromBytes(bytes)
                if (startAt > 0f) {
                    voicePlayer.isPlaying.first { it }
                    voicePlayer.seekTo(startAt)
                }
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
            combine(voicePlayer.isPlaying, voicePlayer.isPaused, voicePlayer.progress, voicePlayer.positionMs) { playing, paused, progress, position ->
                PlaybackSnapshot(playing, paused, progress, position)
            }.collect { p ->
                val finished = wasPlaying && !p.playing && !p.paused
                wasPlaying = p.playing
                _uiState.update { s ->
                    when {
                        s.playingMessageId == null -> s
                        finished -> s.copy(playingMessageId = null, isVoicePaused = false, playbackProgress = 0f, playbackPositionMs = 0L)
                        else -> s.copy(isVoicePaused = p.paused, playbackProgress = p.progress, playbackPositionMs = p.position)
                    }
                }
            }
        }
    }

    private data class PlaybackSnapshot(val playing: Boolean, val paused: Boolean, val progress: Float, val position: Long)

    // ---------- Private messages ----------


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
        _uiState.update { it.copy(isGuest = repository.isGuest(), currentUserId = repository.currentUserId() ?: "") }
        repository.setActive(true)
        viewModelScope.launch { repository.refreshBlocks() }
        checkBan()
    }

    /** Opens (or starts) a private chat with someone from Speaky Hub. */
    /** Premium, or free private messages left today. */
    fun onMessageUserClicked(userId: String, nickname: String) {
        viewModelScope.launch { repository.openPrivateChat("Message $nickname") { onMessageUser(userId, nickname) } }
    }

    fun onMessageUser(userId: String, nickname: String) {
        if (userId == _uiState.value.currentUserId || userId.isEmpty()) return
        if (_uiState.value.isGuest) {
            _uiState.update { it.copy(error = "Sign in with Google first to send private messages.") }
            return
        }
        navigationManager.navigate(RoomDestination(draftDirectRoomId(userId), nickname))
    }

    fun onDeleteChatRequested() = _uiState.update { it.copy(confirmDeleteChat = true) }
    fun onDeleteChatDismissed() = _uiState.update { it.copy(confirmDeleteChat = false) }

    /** Deletes this private chat for me only, then leaves it. */
    fun onConfirmDeleteChat() {
        _uiState.update { it.copy(confirmDeleteChat = false) }
        if (draftOtherUserId != null) {
            navigationManager.back()
            return
        }
        viewModelScope.launch {
            try {
                repository.clearPrivateChat(roomId)
                navigationManager.back()
            } catch (e: Exception) {
                Napier.w("clearPrivateChat failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't delete the chat.")) }
            }
        }
    }

    fun onToggleMute() {
        if (draftOtherUserId != null) return
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
    fun onAdminBanRequested(user: CommunityUser) = _uiState.update { it.copy(profileUser = null, adminBanTarget = user) }
    fun onAdminBanCancelled() = _uiState.update { it.copy(adminBanTarget = null) }

    fun onConfirmAdminBan(removeMessages: Boolean) {
        val user = _uiState.value.adminBanTarget ?: return
        _uiState.update { it.copy(adminBanTarget = null) }
        viewModelScope.launch {
            try {
                repository.adminBan(user.uid, "other", removeMessages)
                _uiState.update { s ->
                    s.copy(
                        messages = if (removeMessages) s.messages.filterNot { it.senderId == user.uid } else s.messages,
                        info = "${user.nickname} is banned.",
                    )
                }
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
                        messages = s.messages.map { if (it.senderId == user.uid) it.copy(senderPhotoUrl = null) else it },
                        info = "${user.nickname}'s photo was removed. They'll be asked for a modest one.",
                    )
                }
            } catch (e: Exception) {
                Napier.w("adminRejectPhoto failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Couldn't remove the photo.")) }
            }
        }
    }

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
        private const val WAVEFORM_BARS = 48
        private const val LIVE_BARS = 40
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
