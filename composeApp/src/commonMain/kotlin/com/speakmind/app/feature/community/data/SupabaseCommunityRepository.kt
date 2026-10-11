package com.speakmind.app.feature.community.data

import com.speakmind.app.db.Room_message_cache
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.GroupMember
import com.speakmind.app.feature.community.data.model.GroupRole
import com.speakmind.app.feature.community.data.model.MessageKind
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.model.TypingUser
import com.speakmind.app.feature.community.data.repository.BlockedException
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.DAILY_MESSAGE_LIMIT
import com.speakmind.app.feature.community.data.repository.ReportReason
import com.speakmind.app.feature.community.data.repository.RoomConnection
import io.github.aakira.napier.Napier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
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
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
private const val DEVICE_TOKENS = "device_tokens"
private const val MESSAGE_COLUMNS =
    "id,room_id,sender_id,kind,body,voice_path,voice_duration_ms,created_at,edited_at," +
        "sender:community_users(id,nickname,gender,photo_url,last_seen)"
private const val ROOM_COLUMNS =
    "id,kind,name,description,last_message_at,last_message_preview,member_count,is_private"
private const val USER_COLUMNS = "id,nickname,gender,photo_url,last_seen"

/** Messages kept on the device per chat for instant/offline display. */
private const val CACHE_PER_ROOM = 200L

class SupabaseCommunityRepository(
    private val client: SupabaseClient,
    private val database: SpeakyDatabase,
) : CommunityRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val userCache = mutableMapOf<String, CommunityUser>()
    private val userCacheLock = Mutex()
    private val voiceCache = LinkedHashMap<String, ByteArray>()
    private var lastPushToken: String? = null

    // ---------- Auth & profile ----------

    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun hasSession(): Boolean {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull() != null
    }

    override fun isGuest(): Boolean = client.auth.currentUserOrNull()?.let { user ->
        user.identities.isNullOrEmpty() || user.appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull == "anonymous"
    } ?: false

    override suspend fun signInAsGuest() {
        client.auth.awaitInitialization()
        if (client.auth.currentUserOrNull() == null) client.auth.signInAnonymously()
        lastPushToken?.let { registerPushToken(it) }
    }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String) {
        // A guest switching to Google starts a fresh account (guests can only post in Speaky Hub).
        if (client.auth.currentUserOrNull() != null && isGuest()) signOut()
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
        lastPushToken?.let { registerPushToken(it) }
    }

    override suspend fun signOut() {
        setActiveCountToZero()
        try {
            lastPushToken?.let { token ->
                client.from(DEVICE_TOKENS).delete { filter { eq("token", token) } }
            }
        } catch (_: Exception) {
        }
        try {
            client.auth.signOut()
        } finally {
            clearLocalCommunityData()
        }
    }

    override suspend fun deleteAccount() {
        setActiveCountToZero()
        client.postgrest.rpc("delete_my_account")
        try {
            client.auth.signOut()
        } catch (_: Exception) {
            // The session's user no longer exists; clearing locally is enough.
        }
        clearLocalCommunityData()
    }

    private fun clearLocalCommunityData() {
        database.speakMindQueries.upsertCommunityProfile(firebase_uid = "", nickname = "", gender = "", photo_url = "")
        database.speakMindQueries.clearRoomMessageCache()
        _blockedUserIds.value = emptySet()
        voiceCache.clear()
    }

    override fun authDisplayName(): String? {
        val meta = client.auth.currentUserOrNull()?.userMetadata ?: return null
        val name = (meta["full_name"] ?: meta["name"])?.jsonPrimitive?.contentOrNull
        return name?.takeIf { it.isNotBlank() }
    }

    override suspend fun isBanned(): Boolean {
        val uid = currentUserId() ?: return false
        return try {
            client.from(USERS).select(Columns.list("id", "banned_at")) {
                filter { eq("id", uid) }
            }.decodeSingleOrNull<BanDto>()?.bannedAt != null
        } catch (_: Exception) {
            false
        }
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
            client.from(USERS).select(Columns.raw(USER_COLUMNS)) {
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
        return client.from(USERS).select(Columns.raw(USER_COLUMNS)) {
            filter {
                if (me != null) neq("id", me)
                exact("banned_at", null)
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
        return client.from(ROOMS).select(Columns.raw(ROOM_COLUMNS)) {
            filter {
                eq("kind", "group")
                eq("is_private", false)
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
        return client.from(ROOMS).select(Columns.raw(ROOM_COLUMNS)) {
            filter { eq("id", roomId) }
        }.decodeSingleOrNull<RoomDto>()?.toDomain(isMember = false)
    }

    override suspend fun roomMembers(roomId: String): List<GroupMember> =
        client.postgrest.rpc("room_member_list", buildJsonObject { put("p_room", roomId) })
            .decodeList<MemberListDto>()
            .map { it.toDomain() }
            .onEach { cacheUser(it.user) }

    override suspend fun createGroup(name: String, description: String, isPrivate: Boolean): ChatRoom {
        val uid = requireUserId()
        val dto = client.from(ROOMS).insert(
            NewGroupDto(name = name.trim(), description = description.trim(), createdBy = uid, isPrivate = isPrivate)
        ) { select(Columns.raw(ROOM_COLUMNS)) }.decodeSingle<RoomDto>()
        return dto.toDomain(isMember = true).copy(myRole = GroupRole.OWNER)
    }

    override suspend fun joinGroup(roomId: String) {
        val uid = requireUserId()
        client.from(MEMBERS).upsert(MemberInsertDto(roomId = roomId, userId = uid)) {
            ignoreDuplicates = true
        }
    }

    override suspend fun joinGroupByCode(code: String): String =
        client.postgrest.rpc("join_group_by_code", buildJsonObject { put("p_code", code.trim().lowercase()) })
            .decodeAs<String>()

    override suspend fun leaveRoom(roomId: String) {
        val uid = currentUserId() ?: return
        client.from(MEMBERS).delete {
            filter {
                eq("room_id", roomId)
                eq("user_id", uid)
            }
        }
    }

    override suspend fun updateGroup(roomId: String, name: String, description: String, isPrivate: Boolean) {
        client.postgrest.rpc(
            "update_group",
            buildJsonObject {
                put("p_room", roomId)
                put("p_name", name.trim())
                put("p_description", description.trim())
                put("p_is_private", isPrivate)
            },
        )
    }

    override suspend fun regenerateInviteCode(roomId: String): String =
        client.postgrest.rpc("regenerate_invite_code", buildJsonObject { put("p_room", roomId) }).decodeAs<String>()

    override suspend fun removeMember(roomId: String, userId: String) {
        client.postgrest.rpc(
            "remove_group_member",
            buildJsonObject {
                put("p_room", roomId)
                put("p_user", userId)
            },
        )
    }

    override suspend fun setMemberRole(roomId: String, userId: String, role: GroupRole) {
        client.postgrest.rpc(
            "set_group_member_role",
            buildJsonObject {
                put("p_room", roomId)
                put("p_user", userId)
                put("p_role", role.name.lowercase())
            },
        )
    }

    override suspend fun deleteGroup(roomId: String) {
        client.postgrest.rpc("delete_group", buildJsonObject { put("p_room", roomId) })
        database.speakMindQueries.trimRoomMessageCache(roomId, 0)
    }

    override suspend fun setMuted(roomId: String, muted: Boolean) {
        val uid = requireUserId()
        client.from(MEMBERS).update({ set("muted", muted) }) {
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
        if (e.message?.contains("blocked") == true && e.message?.contains("not_allowed") != true) {
            throw BlockedException()
        } else throw e
    }

    override suspend fun markRead(roomId: String) {
        try {
            client.postgrest.rpc("mark_room_read", buildJsonObject { put("p_room", roomId) })
        } catch (_: Exception) {
        }
    }

    override fun observeRoomActivity(): Flow<Unit> = channelFlow {
        val channel = client.channel("activity-${nowMillis()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = MESSAGES
        }
        val job = launch { inserts.collect { send(Unit) } }
        channel.subscribe()
        try {
            awaitCancellation()
        } finally {
            job.cancel()
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
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
                    message.kind == MessageKind.VOICE -> "[voice] ${message.voicePath ?: "expired"}"
                    else -> message.text.take(2000)
                },
            )
        )
    }

    // ---------- Messages ----------

    override suspend fun cachedMessages(roomId: String, limit: Int): List<RoomMessage> =
        database.speakMindQueries.selectCachedRoomMessages(roomId, limit.toLong())
            .executeAsList()
            .map { it.toDomain() }
            .reversed()

    override suspend fun loadMessages(roomId: String, pageSize: Int, before: RoomMessage?): List<RoomMessage> {
        val rows = client.from(MESSAGES).select(Columns.raw(MESSAGE_COLUMNS)) {
            filter {
                eq("room_id", roomId)
                if (before != null && before.createdAtRaw.isNotEmpty()) lt("created_at", before.createdAtRaw)
            }
            order("created_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            limit(pageSize.toLong())
        }.decodeList<MessageDto>()
        rows.mapNotNull { it.sender }.forEach { cacheUser(it.toDomain()) }
        val messages = rows.map { it.toDomain() }.reversed()
        if (before == null) {
            // The newest page replaces what's cached, so deletions made elsewhere disappear too.
            database.transaction {
                database.speakMindQueries.trimRoomMessageCache(roomId, 0)
                messages.forEach { cache(it) }
            }
        } else {
            messages.forEach { cache(it) }
            database.speakMindQueries.trimRoomMessageCache(roomId, CACHE_PER_ROOM)
        }
        return messages
    }

    override suspend fun sendText(roomId: String, messageId: String, text: String): RoomMessage {
        val uid = requireUserId()
        val dto = client.from(MESSAGES).insert(
            NewMessageDto(id = messageId, roomId = roomId, senderId = uid, kind = "text", body = text)
        ) { select(Columns.raw(MESSAGE_COLUMNS)) }.decodeSingle<MessageDto>()
        return dto.toDomain().also { cacheAndTrim(it) }
    }

    override suspend fun sendVoice(roomId: String, messageId: String, audio: ByteArray, durationMs: Long): RoomMessage {
        val uid = requireUserId()
        val path = "$uid/$roomId/$messageId.m4a"
        client.storage.from(VOICE_BUCKET).upload(path, audio) {
            upsert = true
            contentType = ContentType("audio", "mp4")
        }
        rememberVoice(path, audio)
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
        return dto.toDomain().also { cacheAndTrim(it) }
    }

    override suspend fun editMessage(message: RoomMessage, newText: String): RoomMessage {
        val dto = client.from(MESSAGES).update({ set("body", newText) }) {
            select(Columns.raw(MESSAGE_COLUMNS))
            filter { eq("id", message.id) }
        }.decodeSingle<MessageDto>()
        return dto.toDomain().also { cache(it) }
    }

    override suspend fun deleteMessage(message: RoomMessage) {
        client.from(MESSAGES).delete { filter { eq("id", message.id) } }
        database.speakMindQueries.deleteCachedRoomMessage(message.id)
        message.voicePath?.let { path ->
            try {
                client.storage.from(VOICE_BUCKET).delete(path)
            } catch (_: Exception) {
                // Orphaned clips are removed by the hourly storage cleanup.
            }
        }
    }

    override suspend fun downloadVoice(path: String): ByteArray {
        voiceCache[path]?.let { return it }
        return client.storage.from(VOICE_BUCKET).downloadAuthenticated(path).also { rememberVoice(path, it) }
    }

    /** Keeps the last few clips in memory so replaying doesn't re-download. */
    private fun rememberVoice(path: String, bytes: ByteArray) {
        voiceCache[path] = bytes
        while (voiceCache.size > 20) voiceCache.remove(voiceCache.keys.first())
    }

    override fun openRoomConnection(roomId: String): RoomConnection = SupabaseRoomConnection(roomId)

    private inner class SupabaseRoomConnection(private val roomId: String) : RoomConnection {
        private val channel = client.channel("room:$roomId")
        private val connScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val _newMessages = MutableSharedFlow<RoomMessage>(extraBufferCapacity = 64)
        override val newMessages: Flow<RoomMessage> = _newMessages.asSharedFlow()
        private val _edited = MutableSharedFlow<RoomMessage>(extraBufferCapacity = 64)
        override val editedMessages: Flow<RoomMessage> = _edited.asSharedFlow()
        private val _deleted = MutableSharedFlow<String>(extraBufferCapacity = 64)
        override val deletedMessageIds: Flow<String> = _deleted.asSharedFlow()
        private val _readMarkers = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 64)
        override val readMarkers: Flow<Pair<String, Long>> = _readMarkers.asSharedFlow()
        private val typingExpiry = MutableStateFlow<Map<String, Pair<TypingUser, Long>>>(emptyMap())
        private val _typingUsers = MutableStateFlow<List<TypingUser>>(emptyList())
        override val typingUsers: StateFlow<List<TypingUser>> = _typingUsers.asStateFlow()

        override suspend fun connect() {
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = MESSAGES
                filter("room_id", FilterOperator.EQ, roomId)
            }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = MESSAGES
                filter("room_id", FilterOperator.EQ, roomId)
            }
            // Delete events can't be filtered server-side; ids from other rooms are ignored by the UI.
            val deletes = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = MESSAGES
            }
            val reads = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = MEMBERS
                filter("room_id", FilterOperator.EQ, roomId)
            }
            val typing = channel.broadcastFlow<TypingDto>(event = "typing")

            connScope.launch {
                inserts.collect { action ->
                    decodeMessage { action.decodeRecord<MessageDto>() }?.let {
                        cacheAndTrim(it)
                        _newMessages.emit(it)
                    }
                }
            }
            connScope.launch {
                updates.collect { action ->
                    decodeMessage { action.decodeRecord<MessageDto>() }?.let {
                        cache(it)
                        _edited.emit(it)
                    }
                }
            }
            connScope.launch {
                deletes.collect { action ->
                    val id = try {
                        action.decodeOldRecord<IdDto>().id
                    } catch (_: Exception) {
                        null
                    } ?: return@collect
                    database.speakMindQueries.deleteCachedRoomMessage(id)
                    _deleted.emit(id)
                }
            }
            connScope.launch {
                reads.collect { action ->
                    try {
                        val m = action.decodeRecord<ReadMarkerDto>()
                        _readMarkers.emit(m.userId to parseMillis(m.lastReadAt))
                    } catch (_: Exception) {
                    }
                }
            }
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

        private suspend fun decodeMessage(decode: () -> MessageDto): RoomMessage? = try {
            val dto = decode()
            dto.toDomain(senderOverride = lookupUser(dto.senderId))
        } catch (e: Exception) {
            Napier.w("Bad realtime message", e)
            null
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

    // ---------- Push notifications ----------

    override suspend fun registerPushToken(token: String) {
        lastPushToken = token
        val uid = currentUserId() ?: return
        try {
            client.from(DEVICE_TOKENS).upsert(
                DeviceTokenDto(token = token, userId = uid, updatedAt = Clock.System.now().toString())
            )
        } catch (e: Exception) {
            Napier.w("registerPushToken failed", e)
        }
    }

    // ---------- Helpers ----------

    private suspend fun cacheUser(user: CommunityUser) {
        userCacheLock.withLock { userCache[user.uid] = user }
    }

    private suspend fun lookupUser(uid: String): CommunityUser? {
        userCacheLock.withLock { userCache[uid] }?.let { return it }
        return try {
            client.from(USERS).select(Columns.raw(USER_COLUMNS)) {
                filter { eq("id", uid) }
            }.decodeSingleOrNull<UserDto>()?.toDomain()?.also { cacheUser(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun cache(m: RoomMessage) {
        if (m.isPending || m.isFailed) return
        database.speakMindQueries.upsertCachedRoomMessage(
            id = m.id,
            room_id = m.roomId,
            sender_id = m.senderId,
            sender_nickname = m.senderNickname,
            sender_gender = m.senderGender,
            sender_photo_url = m.senderPhotoUrl,
            kind = if (m.kind == MessageKind.VOICE) "voice" else "text",
            body = m.text,
            voice_path = m.voicePath,
            voice_duration_ms = m.voiceDurationMs,
            created_at = m.createdAtMillis,
            created_at_raw = m.createdAtRaw,
            edited = if (m.isEdited) 1L else 0L,
        )
    }

    private fun cacheAndTrim(m: RoomMessage) {
        cache(m)
        database.speakMindQueries.trimRoomMessageCache(m.roomId, CACHE_PER_ROOM)
    }

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
            voicePath = voicePath,
            voiceDurationMs = voiceDurationMs,
            createdAtMillis = parseMillis(createdAt),
            createdAtRaw = createdAt,
            isEdited = editedAt != null,
        )
    }
}

private fun Room_message_cache.toDomain() = RoomMessage(
    id = id,
    roomId = room_id,
    senderId = sender_id,
    senderNickname = sender_nickname,
    senderGender = sender_gender,
    senderPhotoUrl = sender_photo_url,
    kind = if (kind == "voice") MessageKind.VOICE else MessageKind.TEXT,
    text = body,
    voicePath = voice_path,
    voiceDurationMs = voice_duration_ms,
    createdAtMillis = created_at,
    createdAtRaw = created_at_raw,
    isEdited = edited == 1L,
)

private fun nowMillis() = Clock.System.now().toEpochMilliseconds()

private fun parseMillis(ts: String?): Long =
    ts?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

private fun parseKind(kind: String) = when (kind) {
    "channel" -> RoomKind.CHANNEL
    "group" -> RoomKind.GROUP
    else -> RoomKind.DIRECT
}

private fun parseRole(role: String?) = when (role) {
    "owner" -> GroupRole.OWNER
    "admin" -> GroupRole.ADMIN
    "member" -> GroupRole.MEMBER
    else -> null
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
private data class BanDto(val id: String, @SerialName("banned_at") val bannedAt: String? = null)

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
    @SerialName("is_private") val isPrivate: Boolean = false,
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
        isPrivate = isPrivate,
    )
}

@Serializable
private data class MyRoomDto(
    @SerialName("room_id") val roomId: String,
    val kind: String,
    val name: String = "",
    val description: String = "",
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String = "",
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("my_role") val myRole: String? = null,
    val muted: Boolean = false,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("invite_code") val inviteCode: String? = null,
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
        description = description,
        lastMessageAtMillis = parseMillis(lastMessageAt),
        lastMessagePreview = lastMessagePreview,
        memberCount = memberCount,
        unreadCount = unreadCount,
        isMember = true,
        myRole = parseRole(myRole),
        muted = muted,
        isPrivate = isPrivate,
        inviteCode = inviteCode,
        otherUser = if (otherUserId != null && otherNickname != null) {
            CommunityUser(otherUserId, otherNickname, otherGender ?: "male", parseMillis(otherLastSeen), otherPhotoUrl)
        } else null,
    )
}

@Serializable
private data class MemberListDto(
    @SerialName("user_id") val userId: String,
    val role: String = "member",
    @SerialName("last_read_at") val lastReadAt: String? = null,
    val nickname: String = "",
    val gender: String = "male",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
) {
    fun toDomain() = GroupMember(
        user = CommunityUser(userId, nickname, gender, parseMillis(lastSeen), photoUrl),
        role = parseRole(role) ?: GroupRole.MEMBER,
        lastReadAtMillis = parseMillis(lastReadAt),
    )
}

@Serializable
private data class ReadMarkerDto(
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String? = null,
)

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
    @SerialName("is_private") val isPrivate: Boolean,
)

@Serializable
private data class IdDto(val id: String)

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
    @SerialName("edited_at") val editedAt: String? = null,
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
private data class DeviceTokenDto(
    val token: String,
    @SerialName("user_id") val userId: String,
    val platform: String = "android",
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class TypingDto(
    @SerialName("user_id") val userId: String,
    val nickname: String,
    val typing: Boolean,
)
