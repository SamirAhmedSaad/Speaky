package com.speakmind.app.feature.community.data.repository

import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.GroupMember
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.model.TypingUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CommunityRepository {
    fun currentUserId(): String?

    // Sign-in: Google, or guest (guests can read everything and post text in Speaky Hub only)
    suspend fun hasSession(): Boolean
    fun isGuest(): Boolean
    suspend fun signInAsGuest()
    suspend fun signInWithGoogle(idToken: String, rawNonce: String)
    suspend fun signOut()
    /** Permanently deletes the account and everything it posted. */
    suspend fun deleteAccount()
    /** Name from the auth provider (e.g. Google), used to prefill the nickname. */
    fun authDisplayName(): String?
    suspend fun isBanned(): Boolean

    suspend fun saveUserProfile(nickname: String, gender: String)
    suspend fun getLocalProfile(): CommunityLocalProfile?
    suspend fun getUserNickname(): String
    suspend fun updateUserName(name: String)
    suspend fun checkAndIncrementDailyQuota(): Boolean

    // People
    suspend fun searchUsers(query: String, offset: Int, pageSize: Int): List<CommunityUser>

    // Online / offline
    /** Ids of users with the app open right now. */
    val onlineUserIds: StateFlow<Set<String>>
    /** Call when a community screen becomes visible / hidden. Ref-counted. */
    fun setActive(active: Boolean)

    // Rooms
    suspend fun myRooms(): List<ChatRoom>
    suspend fun getRoom(roomId: String): ChatRoom?
    suspend fun roomMembers(roomId: String): List<GroupMember>
    suspend fun setMuted(roomId: String, muted: Boolean)
    /** Returns the private chat room id with [otherUserId], creating it if needed. */
    suspend fun openDirectChat(otherUserId: String): String
    suspend fun markRead(roomId: String)
    /** Emits whenever a message lands in any room visible to the user. */
    fun observeRoomActivity(): Flow<Unit>

    // Safety
    /** Users I blocked; their messages are hidden and they can't message me privately. */
    val blockedUserIds: StateFlow<Set<String>>
    suspend fun refreshBlocks()
    suspend fun blockUser(userId: String)
    suspend fun unblockUser(userId: String)
    /** True if either of us blocked the other. */
    suspend fun isBlockedBetween(otherUserId: String): Boolean
    suspend fun reportUser(userId: String, reason: ReportReason, details: String, message: RoomMessage?)

    // Messages
    /** Recent messages saved on the device, oldest first (shown instantly / offline). */
    suspend fun cachedMessages(roomId: String, limit: Int): List<RoomMessage>
    /** A page of messages older than [before], oldest first. */
    suspend fun loadMessages(roomId: String, pageSize: Int, before: RoomMessage?): List<RoomMessage>
    suspend fun sendText(roomId: String, messageId: String, text: String): RoomMessage
    suspend fun sendVoice(roomId: String, messageId: String, audio: ByteArray, durationMs: Long): RoomMessage
    suspend fun editMessage(message: RoomMessage, newText: String): RoomMessage
    suspend fun deleteMessage(message: RoomMessage)
    /** Downloads a private voice clip (only chat members can). */
    suspend fun downloadVoice(path: String): ByteArray

    /** Live connection to one room: messages, edits, deletes, read markers, typing. */
    fun openRoomConnection(roomId: String): RoomConnection

    // Push notifications
    suspend fun registerPushToken(token: String)
}

interface RoomConnection {
    val newMessages: Flow<RoomMessage>
    val editedMessages: Flow<RoomMessage>
    val deletedMessageIds: Flow<String>
    /** (userId, lastReadAt epoch millis) whenever a member reads the room. */
    val readMarkers: Flow<Pair<String, Long>>
    val typingUsers: StateFlow<List<TypingUser>>
    suspend fun connect()
    suspend fun sendTyping(isTyping: Boolean)
    suspend fun close()
}

enum class ReportReason(val apiValue: String, val label: String) {
    SPAM("spam", "Spam"),
    HARASSMENT("harassment", "Harassment or bullying"),
    INAPPROPRIATE("inappropriate", "Inappropriate content"),
    SCAM("scam", "Scam or fraud"),
    OTHER("other", "Something else"),
}

/** Thrown when a private chat can't be opened because of a block. */
class BlockedException : Exception("blocked")

/** Maps server errors (bans, limits, guest rules) to a message for the user. */
fun friendlyCommunityError(e: Throwable, fallback: String): String {
    val m = e.message.orEmpty().lowercase()
    return when {
        "banned" in m -> "Your account is suspended for breaking community rules."
        "guest_not_allowed" in m -> "Sign in with Google to use private chats, groups, and voice messages."
        "daily_limit" in m -> "You've reached today's Speaky Hub limit. Try again tomorrow."
        "rate_limited" in m -> "You're sending messages too fast. Wait a moment."
        "not allowed" in m -> "You don't have permission to do that."
        "24 hours" in m -> "Messages can only be edited for 24 hours."
        "blocked" in m -> "You can't message this person."
        "network" in m || "unable to resolve host" in m || "timeout" in m -> "No connection. Check your internet."
        else -> fallback
    }
}

const val DAILY_MESSAGE_LIMIT = 50
