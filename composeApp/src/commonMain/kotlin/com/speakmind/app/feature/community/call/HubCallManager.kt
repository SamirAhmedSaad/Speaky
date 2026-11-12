package com.speakmind.app.feature.community.call

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

enum class CallPhase { Idle, Connecting, InCall, Ended }

/** Why my call ended: shown on the call screen (or as a card when it was minimized). */
enum class CallEnd { NobodyJoined, Admin, CallsOff, Removed, ConnectionLost, Left }

data class HubCallState(
    /** The first server read finished (until then [enabled] and [live] are placeholders). */
    val loaded: Boolean = false,
    /** Admin switch: when false the call button is hidden. */
    val enabled: Boolean = false,
    val isAdmin: Boolean = false,
    /** Banned from calls until this time (Long.MAX_VALUE: for good). */
    val bannedUntil: Long? = null,
    /** The Hub's live call, for the banner (and the grid while I'm in it). */
    val live: LiveCall? = null,
    val phase: CallPhase = CallPhase.Idle,
    val myCallId: String? = null,
    val muted: Boolean = false,
    val speaker: Boolean = true,
    val speaking: Set<String> = emptySet(),
    val ended: CallEnd? = null,
    /** One-off message (full, couldn't connect, …). */
    val message: String? = null,
    /** The connection dropped and the call is being rejoined. */
    val reconnecting: Boolean = false,
) {
    val inCall: Boolean get() = phase == CallPhase.InCall || phase == CallPhase.Connecting
    val regularCount: Int get() = live?.members?.count { !it.premiumSeat } ?: 0
    val premiumCount: Int get() = live?.members?.count { it.premiumSeat } ?: 0
    val isFull: Boolean get() = regularCount >= MAX_SEATS
}

const val MAX_SEATS = 5
const val MAX_PREMIUM_SEATS = 5
const val NOBODY_JOINED_MS = 5 * 60 * 1000L

/**
 * The one Hub call this device can be in. App-wide, so a call keeps going when its screen closes
 * (minimized) or the app goes to the background (with [CallKeepAlive]).
 */
class HubCallManager(private val repository: HubCallRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(HubCallState())
    val state: StateFlow<HubCallState> = _state.asStateFlow()

    /** True while the full call screen is showing (the floating pill hides then). */
    val screenOpen = MutableStateFlow(false)

    private var roomId: String? = null
    private var watchJob: Job? = null
    private var callJobs = mutableListOf<Job>()
    private var engine: VoiceEngine? = null

    fun myId(): String? = repository.myUserId()

    /** Follows the Hub's call and the admin switch. Safe to call again; keeps one subscription. */
    fun watch(roomId: String) {
        if (this.roomId == roomId && watchJob?.isActive == true) return refresh()
        this.roomId = roomId
        watchJob?.cancel()
        watchJob = scope.launch {
            launch {
                val admin = repository.isAdmin()
                _state.update { it.copy(isAdmin = admin) }
            }
            refreshNow()
            repository.changes().collect { refreshNow() }
        }
    }

    /** A "call started" notification was tapped: tell the user when that call is already over. */
    fun openedFromInvite(callId: String) {
        scope.launch {
            val call = runCatching { repository.call(callId) }.getOrNull() ?: return@launch
            if (call.endedAtMillis != null) {
                _state.update { it.copy(message = "This voice call has already ended. You can start a new one from the Hub.") }
            }
        }
    }

    /** The admin switch flipped: show it at once, before the server confirms. */
    fun showEnabled(on: Boolean) = _state.update { it.copy(enabled = on) }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val room = roomId ?: return
        val enabled = repository.callsEnabled()
        val banned = repository.myBanUntil()
        val live = runCatching { repository.liveCall(room) }.getOrElse { _state.value.live }
        _state.update { it.copy(loaded = true, enabled = enabled, bannedUntil = banned, live = live) }
        val me = repository.myUserId()
        val s = _state.value
        // Still seated from before the app was closed: free the seat instead of showing a ghost.
        if (s.phase == CallPhase.Idle && s.myCallId == null && live != null && live.members.any { it.userId == me }) {
            repository.leave(live.id)
            return refreshNow()
        }
        val myCall = s.myCallId ?: return
        if (s.phase != CallPhase.InCall) return
        when {
            live == null || live.id != myCall -> endMine(endReasonOf(repository.call(myCall)?.endReason))
            live.members.none { it.userId == me } -> endMine(CallEnd.Removed)
            else -> {
                // An admin muted me: follow it here too.
                val mine = live.members.first { it.userId == me }
                if (mine.muted && !s.muted) applyMute(true, tellServer = false)
                updateNotification()
            }
        }
    }

    /** Starts the Hub call, or joins it when one is live. */
    fun start() {
        val room = roomId ?: return
        connect { repository.start(room) }
    }

    fun join() {
        val callId = _state.value.live?.id ?: return start()
        connect { repository.join(callId) }
    }

    private fun connect(takeSeat: suspend () -> HubCallRepository.Seat) {
        if (_state.value.inCall) return
        _state.update { it.copy(phase = CallPhase.Connecting, ended = null, message = null, muted = false, speaker = true) }
        scope.launch {
            val seat = try {
                takeSeat()
            } catch (e: CallRefused) {
                _state.update { it.copy(phase = CallPhase.Idle, message = refusalText(e.reason)) }
                refreshNow()
                return@launch
            }
            _state.update { it.copy(myCallId = seat.callId) }
            refreshNow()
            val starterAlone = _state.value.live?.let { it.startedBy == repository.myUserId() && it.members.size <= 1 } ?: false
            val connected = tryProvider(seat.callId) || (
                starterAlone && seat.provider == PROVIDER_AGORA &&
                    runCatching { repository.useFallback(seat.callId) }.getOrNull() == PROVIDER_LIVEKIT &&
                    tryProvider(seat.callId)
                )
            if (!connected) {
                repository.leave(seat.callId)
                _state.update { it.copy(phase = CallPhase.Idle, myCallId = null, message = "Couldn't connect. Your network may block calls. Try another Wi-Fi or mobile data.") }
                return@launch
            }
            _state.update { it.copy(phase = CallPhase.InCall) }
            CallKeepAlive.start(NOTIFICATION_TITLE, notificationText(), muted = false)
            startCallLoops(seat.callId)
            refreshNow()
        }
    }

    /** Fetches a token for the call's current provider and connects within [CONNECT_MS]. */
    private suspend fun tryProvider(callId: String): Boolean {
        val token = try {
            repository.token(callId)
        } catch (e: Exception) {
            Napier.w("voice token failed", e)
            return false
        }
        val candidate = createVoiceEngine(token.provider)
        return try {
            withTimeout(CONNECT_MS) { candidate.join(token, repository.myUserId().orEmpty()) }
            engine = candidate
            true
        } catch (e: Throwable) {
            Napier.w("${token.provider} join failed", e)
            runCatching { candidate.leave() }
            false
        }
    }

    private fun startCallLoops(callId: String) {
        callJobs.forEach { it.cancel() }
        callJobs.clear()
        val active = engine ?: return
        callJobs += scope.launch {
            active.speaking.collect { ids -> _state.update { it.copy(speaking = ids) } }
        }
        callJobs += scope.launch {
            active.connected.collect { ok -> if (!ok && _state.value.phase == CallPhase.InCall) reconnect(callId) }
        }
        callJobs += scope.launch {
            while (true) {
                delay(25_000)
                if (repository.heartbeat(callId) == false) {
                    endMine(endReasonOf(repository.call(callId)?.endReason, removed = true))
                    break
                }
            }
        }
    }

    /** The connection dropped (network change, signal lost): rejoin the same call, twice at most. */
    private fun reconnect(callId: String) {
        scope.launch {
            _state.update { it.copy(reconnecting = true) }
            engine?.leave()
            engine = null
            val muted = _state.value.muted
            repeat(2) { attempt ->
                if (attempt > 0) delay(3_000)
                if (repository.heartbeat(callId) == false) return@launch endMine(endReasonOf(repository.call(callId)?.endReason, removed = true))
                if (tryProvider(callId)) {
                    engine?.setMuted(muted)
                    _state.update { it.copy(reconnecting = false) }
                    startCallLoops(callId)
                    return@launch
                }
            }
            repository.leave(callId)
            endMine(CallEnd.ConnectionLost)
        }
    }

    fun toggleMute() = applyMute(!_state.value.muted, tellServer = true)

    private fun applyMute(muted: Boolean, tellServer: Boolean) {
        engine?.setMuted(muted)
        _state.update { it.copy(muted = muted) }
        updateNotification()
        val callId = _state.value.myCallId ?: return
        if (tellServer) scope.launch { repository.setMuted(callId, muted) }
    }

    fun toggleSpeaker() {
        val on = !_state.value.speaker
        engine?.setSpeaker(on)
        _state.update { it.copy(speaker = on) }
    }

    fun leave() {
        val callId = _state.value.myCallId
        hangUp()
        _state.update { it.copy(phase = CallPhase.Idle, myCallId = null, ended = null) }
        if (callId != null) scope.launch {
            repository.leave(callId)
            refreshNow()
        }
    }

    /** The call screen (or ended card) was closed. */
    fun dismissEnded() = _state.update { it.copy(ended = null, phase = if (it.phase == CallPhase.Ended) CallPhase.Idle else it.phase) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun endMine(reason: CallEnd) {
        hangUp()
        _state.update { it.copy(phase = CallPhase.Ended, myCallId = null, ended = reason) }
    }

    private fun hangUp() {
        callJobs.forEach { it.cancel() }
        callJobs.clear()
        engine?.leave()
        engine = null
        CallKeepAlive.stop()
        _state.update { it.copy(speaking = emptySet(), muted = false, reconnecting = false) }
    }

    private fun updateNotification() {
        if (_state.value.phase == CallPhase.InCall) CallKeepAlive.update(NOTIFICATION_TITLE, notificationText(), _state.value.muted)
    }

    private fun notificationText(): String {
        val count = _state.value.live?.members?.size ?: 1
        return if (count <= 1) "Waiting for others to join" else "$count people in the call"
    }

    // ---------- Admin ----------

    fun adminEnd() = adminAction { callId -> repository.adminEnd(callId) }

    fun adminMute(userId: String) = adminAction { callId -> repository.adminMute(callId, userId) }

    fun adminRemove(userId: String) = adminAction { callId -> repository.adminRemove(callId, userId) }

    fun adminBan(userId: String, days: Int?) {
        scope.launch {
            repository.adminBan(userId, days)
            refreshNow()
        }
    }

    private fun adminAction(action: suspend (String) -> Boolean) {
        val callId = _state.value.live?.id ?: return
        scope.launch {
            if (!action(callId)) _state.update { it.copy(message = "That didn't work. Please try again.") }
            refreshNow()
        }
    }

    /** Milliseconds left before a call nobody joined closes; null once someone joined. */
    fun nobodyJoinedLeftMs(): Long? {
        val live = _state.value.live ?: return null
        if (live.peakMembers > 1) return null
        return (live.startedAtMillis + NOBODY_JOINED_MS - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0)
    }

    private fun endReasonOf(reason: String?, removed: Boolean = false): CallEnd = when (reason) {
        "nobody_joined" -> CallEnd.NobodyJoined
        "admin" -> CallEnd.Admin
        "calls_off" -> CallEnd.CallsOff
        null -> if (removed) CallEnd.Removed else CallEnd.Left
        else -> if (removed) CallEnd.Removed else CallEnd.Left
    }

    private fun refusalText(reason: String): String = when (reason) {
        "full" -> "The call is full."
        "premium_full" -> "All Premium seats are taken too. Try again soon."
        "banned" -> "You can't join voice calls right now."
        "calls_off" -> "Voice calls are turned off right now."
        "ended" -> "That call just ended."
        "not_signed_in" -> "Sign in to the Hub to join calls."
        else -> "Couldn't join the call. Please try again."
    }

    private companion object {
        const val NOTIFICATION_TITLE = "Hub voice call"
        /** Per provider: Agora connects in ~1-3 s; a blocked network never does, so move on soon. */
        const val CONNECT_MS = 8_000L
    }
}
