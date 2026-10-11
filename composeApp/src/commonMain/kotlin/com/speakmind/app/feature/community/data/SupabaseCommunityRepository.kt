package com.speakmind.app.feature.community.data

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.MessageKind
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.model.TypingUser
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.BlockedException
import com.speakmind.app.feature.community.data.repository.DAILY_MESSAGE_LIMIT
import com.speakmind.app.feature.community.data.repository.ReportReason
import com.speakmind.app.feature.community.data.repository.RoomConnection
import io.github.aakira.napier.Napier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val USERS = "community_users"
private const val ROOMS = "rooms"
private const val MEMBERS = "room_members"
private const val MESSAGES = "room_messages"
private const val VOICE_BUCKET = "voice-recordings"
private const val BLOCKS = "user_blocks"
private const val REPORTS = "user_reports"
private const val MESSAGE_COLUMNS =
    "id,room_id,sender_id,kind,body,voice_path,voice_duration_ms,created_at," +
        "sender:community_users(id,nickname,gender,photo_url,last_seen)"

class SupabaseCommunityRepository(
    private val client: SupabaseClient,
    private val database: SpeakyDatabase,
) : CommunityRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val userCache = mutableMapOf<String, CommunityUser>()
    private val userCacheLock = Mutex()

    // ---------- Auth & profile ----------

    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun hasSession(): Boolean {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull() != null
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Boolean {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        // With "Confirm email" on, no session exists until the code/link is confirmed.
        return client.auth.currentSessionOrNull() != null
    }

    override suspend fun sendEmailCode(email: String) {
        client.auth.signInWith(OTP) {
            this.email = email.trim()
            createUser = true
        }
    }

    override suspend fun verifyEmailCode(email: String, code: String, isSignupConfirmation: Boolean) {
        client.auth.verifyEmailOtp(
            type = if (isSignupConfirmation) OtpType.Email.SIGNUP else OtpType.Email.EMAIL,
            email = email.trim(),
            token = code.trim(),
        )
    }

    override suspend fun resendSignupCode(email: String) {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    override suspend fun signOut() {
        setActiveCountToZero()
        try {
            client.auth.signOut()
        } finally {
            database.speakMindQueries.upsertCommunityProfile(firebase_uid = "", nickname = "", gender = "", photo_url = "")
            _blockedUserIds.value = emptySet()
        }
    }

    override fun authDisplayName(): String? {
        val meta = client.auth.currentUserOrNull()?.userMetadata ?: return null
        val name = (meta["full_name"] ?: meta["name"])?.let { it.jsonPrimitive.contentOrNull }
        return name?.takeIf { it.isNotBlank() }
    }

    private suspend fun requireUserId(): String {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.id ?: error("Not signed in")
    }

    override suspend fun saveUserProfile(nickname: String, gender: String) {
        val uid = requireUserId()
        client.from(USERS).upsert(UserUpsertDto(id = uid, nickname = nickname, gender = gender))
        database.speakMindQueries.upsertCommunityProfile(
            firebase_uid = uid,
            nickname = nickname,
            gender = gender,
            photo_url = "",
        )
    }

    override suspend fun getLocalProfile(): CommunityLocalProfile? {
        client.auth.awaitInitialization()
        val uid = client.auth.currentUserOrNull()?.id ?: return null
        val row = database.speakMindQueries.getCommunityProfile().executeAsOneOrNull()
        if (row != null && row.firebase_uid == uid && row.gender.isNotEmpty()) {
            return CommunityLocalProfile(uid, row.nickname, row.gender, row.photo_url.ifEmpty { null })
        }
        // Signed in on a new device: restore the profile saved on the server.
        val remote = try {
            client.from(USERS).select(Columns.list("id", "nickname", "gender", "photo_url", "last_seen")) {
                filter { eq("id", uid) }
            }.decodeSingleOrNull<UserDto>()
        } catch (_: Exception) {
            null
        } ?: return null
        database.speakMindQueries.upsertCommunityProfile(
            firebase_uid = uid,
            nickname = remote.nickname,
            gender = remote.gender,
            photo_url = remote.photoUrl ?: "",
        )
        return CommunityLocalProfile(uid, remote.nickname, remote.gender, remote.photoUrl)
    }

    override suspend fun getUserNickname(): String =
        database.speakMindQueries.selectProgress().executeAsOneOrNull()?.user_name ?: ""

    override suspend fun updateUserName(name: String) {
        database.speakMindQueries.updateUserName(name)
        val profile = getLocalProfile() ?: return
        database.speakMindQueries.upsertCommunityProfile(
            firebase_uid = profile.uid,
            nickname = name,
            gender = profile.gender,
            photo_url = profile.photoUrl ?: "",
        )
        try {
            client.from(USERS).update({ set("nickname", name) }) {
                filter { eq("id", profile.uid) }
            }
        } catch (e: Exception) {
            Napier.w("updateUserName sync failed", e)
        }
    }

    override suspend fun checkAndIncrementDailyQuota(): Boolean {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val row = database.speakMindQueries.getCommunityDailyQuota().executeAsOneOrNull()
        val currentCount = if (row == null || row.date != today) {
            database.speakMindQueries.upsertCommunityDailyQuota(today, 0)
            0
        } else {
            row.messages_sent.toInt()
        }
        if (currentCount >= DAILY_MESSAGE_LIMIT) return false
        database.speakMindQueries.incrementDailyMessageCount()
        return true
    }

    // ---------- People ----------

    override suspend fun searchUsers(query: String, offset: Int, pageSize: Int): List<CommunityUser> {
        val me = currentUserId()
        val q = query.trim().lowercase()
        return client.from(USERS).select(Columns.list("id", "nickname", "gender", "photo_url", "last_seen")) {
            filter {
                if (me != null) neq("id", me)
                if (q.isNotEmpty()) ilike("nickname_search", "%${q.replace("%", "").replace("_", "")}%")
            }
            order("last_seen", Order.DESCENDING)
            range(offset.toLong(), (offset + pageSize - 1).toLong())
        }.decodeList<UserDto>().map { it.toDomain() }.onEach { cacheUser(it) }
    }

    // ---------- Presence (online / offline) ----------

    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    override val onlineUserIds: StateFlow<Set<String>> = _onlineUserIds.asStateFlow()

    private var presenceChannel: RealtimeChannel? = null
    private var presenceJob: Job? = null
    private var heartbeatJob: Job? = null
    private var deactivateJob: Job? = null
    private var activeCount = 0
    private val presenceLock = Mutex()

    override fun setActive(active: Boolean) {
        scope.launch {
            presenceLock.withLock {
                activeCount = (activeCount + if (active) 1 else -1).coerceAtLeast(0)
                if (activeCount > 0) {
                    deactivateJob?.cancel()
                    deactivateJob = null
                    if (presenceChannel == null) goOnline()
                } else if (deactivateJob == null) {
                    // Small grace period so moving between community screens doesn't flicker offline.
                    deactivateJob = scope.launch {
                        delay(3_000)
                        presenceLock.withLock {
                            if (activeCount == 0) goOffline()
                            deactivateJob = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun setActiveCountToZero() {
        presenceLock.withLock {
            activeCount = 0
            deactivateJob?.cancel()
            deactivateJob = null
            goOffline()
        }
    }

    private suspend fun goOnline() {
        val uid = currentUserId() ?: return
        try {
            val channel = client.channel("online") {
                presence { key = uid }
            }
            presenceChannel = channel
            presenceJob = scope.launch {
                channel.presenceChangeFlow().collect { action ->
                    _onlineUserIds.update { it + action.joins.keys - action.leaves.keys }
                }
            }
            channel.subscribe(blockUntilSubscribed = true)
            channel.track(buildJsonObject { put("user_id", uid) })
            heartbeatJob = scope.launch {
                while (true) {
                    updateLastSeen(uid)
                    delay(60_000)
                }
            }
        } catch (e: Exception) {
            Napier.w("Presence connect failed", e)
        }
    }

    private suspend fun goOffline() {
        heartbeatJob?.cancel()
        presenceJob?.cancel()
        heartbeatJob = null
        presenceJob = null
        val channel = presenceChannel ?: return
        presenceChannel = null
        try {
            channel.untrack()
            client.realtime.removeChannel(channel)
        } catch (_: Exception) {
        }
        _onlineUserIds.value = emptySet()
        currentUserId()?.let { updateLastSeen(it) }
    }

    private suspend fun updateLastSeen(uid: String) {
        try {
            client.from(USERS).update({ set("last_seen", Clock.System.now().toString()) }) {
                filter { eq("id", uid) }
            }
        } catch (_: Exception) {
        }
    }

    // ---------- Rooms ----------

    override suspend fun myRooms(): List<ChatRoom> =
        client.postgrest.rpc("my_rooms").decodeList<MyRoomDto>().map { it.toDomain() }

    override suspend fun publicGroups(query: String): List<ChatRoom> {
        val joined = myRooms().filter { it.kind == RoomKind.GROUP }.associateBy { it.id }
        val q = query.trim()
        return client.from(ROOMS).select(Columns.list(ROOM_COLUMNS)) {
            filter {
                eq("kind", "group")
                if (q.isNotEmpty()) ilike("name", "%${q.replace("%", "").replace("_", "")}%")
            }
            order("last_message_at", Order.DESCENDING)
            limit(100)
        }.decodeList<RoomDto>().map { dto ->
            joined[dto.id] ?: dto.toDomain(isMember = false)
        }
    }

    override suspend fun getRoom(roomId: String): ChatRoom? {
        myRooms().firstOrNull { it.id == roomId }?.let { return it }
        return client.from(ROOMS).select(Columns.list(ROOM_COLUMNS)) {
            filter { eq("id", roomId) }
        }.decodeSingleOrNull<RoomDto>()?.toDomain(isMember = false)
    }

    override suspend fun roomMemberIds(roomId: String): List<String> =
        client.from(MEMBERS).select(Columns.list("user_id")) {
            filter { eq("room_id", roomId) }
        }.decodeList<MemberDto>().map { it.userId }

    override suspend fun createGroup(name: String, description: String): ChatRoom {
        val uid = currentUserId() ?: error("Not signed in")
        val dto = client.from(ROOMS).insert(
            NewGroupDto(name = name.trim(), description = description.trim(), createdBy = uid)
        ) { select(Columns.list(ROOM_COLUMNS)) }.decodeSingle<RoomDto>()
        return dto.toDomain(isMember = true)
    }

    override suspend fun joinGroup(roomId: String) {
        val uid = currentUserId() ?: error("Not signed in")
        client.from(MEMBERS).upsert(MemberInsertDto(roomId = roomId, userId = uid)) {
            ignoreDuplicates = true
        }
    }

    override suspend fun leaveRoom(roomId: String) {
        val uid = currentUserId() ?: return
        client.from(MEMBERS).delete {
            filter {
                eq("room_id", roomId)
                eq("user_id", uid)
            }
        }
    }

    override suspend fun openDirectChat(otherUserId: String): String = try {
        client.postgrest.rpc("get_or_create_dm", buildJsonObject { put("p_other", otherUserId) })
            .decodeAs<String>()
    } catch (e: Exception) {
        if (e.message?.contains("blocked") == true) throw BlockedException() else throw e
    }

    override suspend fun markRead(roomId: String) {
        try {
            client.postgrest.rpc("mark_room_read", buildJsonObject { put("p_room", roomId) })
        } catch (_: Exception) {
        }
    }

    override fun observeRoomActivity(): Flow<Unit> = kotlinx.coroutines.flow.channelFlow {
        val channel = client.channel("activity-${Clock.System.now().toEpochMilliseconds()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = MESSAGES
        }
        val job = launch { inserts.collect { send(Unit) } }
        channel.subscribe()
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            job.cancel()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                client.realtime.removeChannel(channel)
            }
        }
    }

    // ---------- Safety ----------

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    override suspend fun refreshBlocks() {
        val uid = currentUserId() ?: return
        try {
            _blockedUserIds.value = client.from(BLOCKS).select(Columns.list("blocked_id")) {
                filter { eq("blocker_id", uid) }
            }.decodeList<BlockDto>().map { it.blockedId }.toSet()
        } catch (e: Exception) {
            Napier.w("refreshBlocks failed", e)
        }
    }

    override suspend fun blockUser(userId: String) {
        val uid = requireUserId()
        client.from(BLOCKS).upsert(BlockInsertDto(blockerId = uid, blockedId = userId)) { ignoreDuplicates = true }
        _blockedUserIds.update { it + userId }
    }

    override suspend fun unblockUser(userId: String) {
        val uid = requireUserId()
        client.from(BLOCKS).delete {
            filter {
                eq("blocker_id", uid)
                eq("blocked_id", userId)
            }
        }
        _blockedUserIds.update { it - userId }
    }

    override suspend fun isBlockedBetween(otherUserId: String): Boolean {
        val uid = currentUserId() ?: return false
        return client.postgrest.rpc(
            "is_blocked_between",
            buildJsonObject {
                put("a", uid)
                put("b", otherUserId)
            },
        ).decodeAs<Boolean>()
    }

    override suspend fun reportUser(userId: String, reason: ReportReason, details: String, message: RoomMessage?) {
        val uid = requireUserId()
        client.from(REPORTS).insert(
            ReportInsertDto(
                reporterId = uid,
                reportedUserId = userId,
                messageId = message?.id?.takeIf { !message.isPending },
                roomId = message?.roomId,
                reason = reason.apiValue,
                details = details.take(500),
                messageSnapshot = when {
                    message == null -> ""
                    message.kind == MessageKind.VOICE -> "[voice] ${message.voiceUrl ?: "expired"}"
                    else -> message.text.take(2000)
                },
            )
        )
    }

    // ---------- Messages ----------

    override suspend fun loadMessages(roomId: String, pageSize: Int, before: RoomMessage?): List<RoomMessage> {
        val rows = client.from(MESSAGES).select(Columns.raw(MESSAGE_COLUMNS)) {
            filter {
                eq("room_id", roomId)
                if (before != null) lt("created_at", before.createdAtRaw)
            }
            order("created_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            limit(pageSize.toLong())
        }.decodeList<MessageDto>()
        rows.mapNotNull { it.sender }.forEach { cacheUser(it.toDomain()) }
        return rows.map { it.toDomain() }.reversed()
    }

    override suspend fun sendText(roomId: String, messageId: String, text: String): RoomMessage {
        val uid = currentUserId() ?: error("Not signed in")
        val dto = client.from(MESSAGES).insert(
            NewMessageDto(id = messageId, roomId = roomId, senderId = uid, kind = "text", body = text)
        ) { select(Columns.raw(MESSAGE_COLUMNS)) }.decodeSingle<MessageDto>()
        return dto.toDomain()
    }

    override suspend fun sendVoice(roomId: String, messageId: String, audio: ByteArray, durationMs: Long): RoomMessage {
        val uid = currentUserId() ?: error("Not signed in")
        val path = "$uid/$roomId/$messageId.m4a"
        client.storage.from(VOICE_BUCKET).upload(path, audio) {
            upsert = true
            contentType = ContentType("audio", "mp4")
        }
        val dto = client.from(MESSAGES).insert(
            NewMessageDto(
                id = messageId,
                roomId = roomId,
                senderId = uid,
                kind = "voice",
                body = "",
                voicePath = path,
                voiceDurationMs = durationMs,
                voiceSizeBytes = audio.size.toLong(),
            )
        ) { select(Columns.raw(MESSAGE_COLUMNS)) }.decodeSingle<MessageDto>()
        return dto.toDomain()
    }

    override fun openRoomConnection(roomId: String): RoomConnection = SupabaseRoomConnection(roomId)

    private inner class SupabaseRoomConnection(private val roomId: String) : RoomConnection {
        private val channel = client.channel("room:$roomId")
        private val connScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val _newMessages = MutableSharedFlow<RoomMessage>(extraBufferCapacity = 64)
        override val newMessages: Flow<RoomMessage> = _newMessages.asSharedFlow()
        private val typingExpiry = MutableStateFlow<Map<String, Pair<TypingUser, Long>>>(emptyMap())
        private val _typingUsers = MutableStateFlow<List<TypingUser>>(emptyList())
        override val typingUsers: StateFlow<List<TypingUser>> = _typingUsers.asStateFlow()

        override suspend fun connect() {
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = MESSAGES
                filter("room_id", FilterOperator.EQ, roomId)
            }.mapNotNull { action ->
                try {
                    val dto = action.decodeRecord<MessageDto>()
                    dto.toDomain(senderOverride = lookupUser(dto.senderId))
                } catch (e: Exception) {
                    Napier.w("Bad realtime message", e)
                    null
                }
            }
            val typing = channel.broadcastFlow<TypingDto>(event = "typing")

            connScope.launch { inserts.collect { _newMessages.emit(it) } }
            connScope.launch {
                typing.collect { t ->
                    typingExpiry.update { map ->
                        if (t.typing) {
                            map + (t.userId to (TypingUser(t.userId, t.nickname) to nowMillis() + 5_000))
                        } else {
                            map - t.userId
                        }
                    }
                    publishTyping()
                }
            }
            // Drop typists that stopped broadcasting (e.g. app closed mid-typing)
            connScope.launch {
                while (true) {
                    delay(1_000)
                    val now = nowMillis()
                    typingExpiry.update { map -> map.filterValues { it.second > now } }
                    publishTyping()
                }
            }
            channel.subscribe(blockUntilSubscribed = true)
        }

        private fun publishTyping() {
            val list = typingExpiry.value.values.map { it.first }
            if (list != _typingUsers.value) _typingUsers.value = list
        }

        override suspend fun sendTyping(isTyping: Boolean) {
            val uid = currentUserId() ?: return
            val nickname = getLocalProfile()?.nickname ?: ""
            try {
                channel.broadcast(event = "typing", message = TypingDto(uid, nickname, isTyping))
            } catch (_: Exception) {
            }
        }

        override suspend fun close() {
            connScope.coroutineContext[Job]?.cancel()
            try {
                client.realtime.removeChannel(channel)
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Helpers ----------

    private suspend fun cacheUser(user: CommunityUser) {
        userCacheLock.withLock { userCache[user.uid] = user }
    }

    private suspend fun lookupUser(uid: String): CommunityUser? {
        userCacheLock.withLock { userCache[uid] }?.let { return it }
        return try {
            client.from(USERS).select(Columns.list("id", "nickname", "gender", "photo_url", "last_seen")) {
                filter { eq("id", uid) }
            }.decodeSingleOrNull<UserDto>()?.toDomain()?.also { cacheUser(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun publicVoiceUrl(path: String?): String? =
        path?.let { client.storage.from(VOICE_BUCKET).publicUrl(it) }

    private fun MessageDto.toDomain(senderOverride: CommunityUser? = null): RoomMessage {
        val s = senderOverride ?: sender?.toDomain()
        return RoomMessage(
            id = id,
            roomId = roomId,
            senderId = senderId,
            senderNickname = s?.nickname ?: "",
            senderGender = s?.gender ?: "male",
            senderPhotoUrl = s?.photoUrl,
            kind = if (kind == "voice") MessageKind.VOICE else MessageKind.TEXT,
            text = body,
            voiceUrl = publicVoiceUrl(voicePath),
            voiceDurationMs = voiceDurationMs,
            createdAtMillis = parseMillis(createdAt),
            createdAtRaw = createdAt,
        )
    }
}

private const val ROOM_COLUMNS =
    "id,kind,name,description,last_message_at,last_message_preview,member_count"

private fun nowMillis() = Clock.System.now().toEpochMilliseconds()

private fun parseMillis(ts: String?): Long =
    ts?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

private fun parseKind(kind: String) = when (kind) {
    "channel" -> RoomKind.CHANNEL
    "group" -> RoomKind.GROUP
    else -> RoomKind.DIRECT
}

// ---------- DTOs ----------

@Serializable
private data class UserDto(
    val id: String,
    val nickname: String,
    val gender: String = "male",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
) {
    fun toDomain() = CommunityUser(id, nickname, gender, parseMillis(lastSeen), photoUrl)
}

@Serializable
private data class UserUpsertDto(
    val id: String,
    val nickname: String,
    val gender: String,
)

@Serializable
private data class RoomDto(
    val id: String,
    val kind: String,
    val name: String = "",
    val description: String = "",
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String = "",
    @SerialName("member_count") val memberCount: Int = 0,
) {
    fun toDomain(isMember: Boolean) = ChatRoom(
        id = id,
        kind = parseKind(kind),
        name = name,
        description = description,
        lastMessageAtMillis = parseMillis(lastMessageAt),
        lastMessagePreview = lastMessagePreview,
        memberCount = memberCount,
        isMember = isMember,
    )
}

@Serializable
private data class MyRoomDto(
    @SerialName("room_id") val roomId: String,
    val kind: String,
    val name: String = "",
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String = "",
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("other_user_id") val otherUserId: String? = null,
    @SerialName("other_nickname") val otherNickname: String? = null,
    @SerialName("other_gender") val otherGender: String? = null,
    @SerialName("other_photo_url") val otherPhotoUrl: String? = null,
    @SerialName("other_last_seen") val otherLastSeen: String? = null,
) {
    fun toDomain() = ChatRoom(
        id = roomId,
        kind = parseKind(kind),
        name = name,
        lastMessageAtMillis = parseMillis(lastMessageAt),
        lastMessagePreview = lastMessagePreview,
        memberCount = memberCount,
        unreadCount = unreadCount,
        isMember = true,
        otherUser = if (otherUserId != null && otherNickname != null) {
            CommunityUser(otherUserId, otherNickname, otherGender ?: "male", parseMillis(otherLastSeen), otherPhotoUrl)
        } else null,
    )
}

@Serializable
private data class MemberDto(@SerialName("user_id") val userId: String)

@Serializable
private data class MemberInsertDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class NewGroupDto(
    val kind: String = "group",
    val name: String,
    val description: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class MessageDto(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String = "text",
    val body: String = "",
    @SerialName("voice_path") val voicePath: String? = null,
    @SerialName("voice_duration_ms") val voiceDurationMs: Long = 0,
    @SerialName("created_at") val createdAt: String,
    val sender: UserDto? = null,
)

@Serializable
private data class NewMessageDto(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String,
    val body: String,
    @SerialName("voice_path") val voicePath: String? = null,
    @SerialName("voice_duration_ms") val voiceDurationMs: Long = 0,
    @SerialName("voice_size_bytes") val voiceSizeBytes: Long = 0,
)

@Serializable
private data class BlockDto(@SerialName("blocked_id") val blockedId: String)

@Serializable
private data class BlockInsertDto(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
)

@Serializable
private data class ReportInsertDto(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("reported_user_id") val reportedUserId: String,
    @SerialName("message_id") val messageId: String?,
    @SerialName("room_id") val roomId: String?,
    val reason: String,
    val details: String,
    @SerialName("message_snapshot") val messageSnapshot: String,
)

@Serializable
private data class TypingDto(
    @SerialName("user_id") val userId: String,
    val nickname: String,
    val typing: Boolean,
)
