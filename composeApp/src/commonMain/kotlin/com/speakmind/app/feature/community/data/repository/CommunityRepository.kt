package com.speakmind.app.feature.community.data.repository

import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.model.TypingUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CommunityRepository {
    fun currentUserId(): String?

    // Sign-in (a real account is required for the community)
    suspend fun hasSession(): Boolean
    suspend fun signInWithEmail(email: String, password: String)
    /** Returns true if signed in right away, false if the email must be confirmed first. */
    suspend fun signUpWithEmail(email: String, password: String): Boolean
    /** Emails a 6-digit login code (creates the account if needed). */
    suspend fun sendEmailCode(email: String)
    suspend fun verifyEmailCode(email: String, code: String, isSignupConfirmation: Boolean)
    suspend fun resendSignupCode(email: String)
    suspend fun signInWithGoogle(idToken: String, rawNonce: String)
    suspend fun signOut()
    /** Name from the auth provider (e.g. Google), used to prefill the nickname. */
    fun authDisplayName(): String?
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
    suspend fun publicGroups(query: String): List<ChatRoom>
    suspend fun getRoom(roomId: String): ChatRoom?
    suspend fun roomMemberIds(roomId: String): List<String>
    suspend fun createGroup(name: String, description: String): ChatRoom
    suspend fun joinGroup(roomId: String)
    suspend fun leaveRoom(roomId: String)
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
    /** A page of messages older than [before], oldest first. */
    suspend fun loadMessages(roomId: String, pageSize: Int, before: RoomMessage?): List<RoomMessage>
    suspend fun sendText(roomId: String, messageId: String, text: String): RoomMessage
    suspend fun sendVoice(roomId: String, messageId: String, audio: ByteArray, durationMs: Long): RoomMessage

    /** Live connection to one room: new messages and typing indicators. */
    fun openRoomConnection(roomId: String): RoomConnection
}

interface RoomConnection {
    val newMessages: Flow<RoomMessage>
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

const val DAILY_MESSAGE_LIMIT = 50
