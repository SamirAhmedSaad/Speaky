package com.speakmind.app.feature.community.data.repository

import com.speakmind.app.feature.community.data.model.AdminChat
import com.speakmind.app.feature.community.data.model.AdminChatMessage
import com.speakmind.app.feature.community.data.model.AdminReport
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

    // Sign-in: Google only. Visitors who aren't signed in can read Speaky Hub but can't post.
    suspend fun hasSession(): Boolean
    /** True when not signed in with Google (read-only visitor). */
    fun isGuest(): Boolean
    suspend fun signInWithGoogle(idToken: String, rawNonce: String)
    suspend fun signOut()
    /** Permanently deletes the account and everything it posted. */
    suspend fun deleteAccount()
    /** Name from the auth provider (e.g. Google), used to prefill the nickname. */
    fun authDisplayName(): String?
    /** Profile photo from the auth provider (e.g. Google), if any. */
    fun authPhotoUrl(): String?
    suspend fun isBanned(): Boolean

    suspend fun saveUserProfile(nickname: String, gender: String)
    /** Changes nickname/gender of an existing profile. */
    suspend fun updateProfile(nickname: String, gender: String)
    /** Uploads a new profile photo (JPEG/PNG bytes) and returns its URL. */
    suspend fun uploadAvatar(bytes: ByteArray): String
    /** My photo's moderation status ("ok", "pending", "rejected", "unchecked") and reason. */
    suspend fun myPhotoStatus(): Pair<String, String?>?
    /** Admins: remove someone's photo and ask them for a modest one. */
    suspend fun adminRejectPhoto(userId: String)
    /** Saves a fun avatar pick ("avatar:fox") as the profile photo. */
    suspend fun setPhotoUrl(url: String)
    /** Uploads a public image under the signed-in user's avatars folder; returns the path written. */
    suspend fun uploadPublicImage(path: String, bytes: ByteArray)
    suspend fun getUser(userId: String): CommunityUser?
    /** Unread private messages, for the badge on Home (0 when not signed in). */
    suspend fun unreadPrivateCount(): Int
    /** Free users' private messages in the last 24 hours, counted on the server; null if unknown (offline, guest). */
    suspend fun freePrivateMessages(): FreeMessages?
    /** AI fixes the message's English, or translates it to English from any language (server-side, limited per day). */
    suspend fun correctMessage(text: String, premium: Boolean): AiCorrection
    suspend fun getLocalProfile(): CommunityLocalProfile?
    suspend fun getUserNickname(): String
    suspend fun updateUserName(name: String)
    suspend fun checkAndIncrementDailyQuota(): Boolean

    // People
    /** [level] filters by CEFR level; [onlyIds] limits to these users (e.g. who's online). */
    suspend fun searchUsers(
        query: String,
        offset: Int,
        pageSize: Int,
        level: String? = null,
        onlyIds: Set<String>? = null,
    ): List<CommunityUser>

    // Online / offline
    /** Ids of users with the app open right now. */
    val onlineUserIds: StateFlow<Set<String>>
    /** Call when a community screen becomes visible / hidden. Ref-counted. */
    fun setActive(active: Boolean)

    // Rooms
    suspend fun myRooms(): List<ChatRoom>
    /** Private chats, newest activity first; pass the last room of the previous page for the next one. */
    suspend fun directChats(pageSize: Int, after: ChatRoom?): List<ChatRoom>
    suspend fun getRoom(roomId: String): ChatRoom?
    suspend fun roomMembers(roomId: String): List<GroupMember>
    suspend fun setMuted(roomId: String, muted: Boolean)

    /** Hides a private chat and its history for me only; a new message brings it back. */
    suspend fun clearPrivateChat(roomId: String)
    /** Returns the private chat room id with [otherUserId], creating it if needed. */
    suspend fun openDirectChat(otherUserId: String): String

    /** Our existing private chat with this person, without creating one. */
    suspend fun findDirectChat(otherUserId: String): String?
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
    suspend fun sendText(roomId: String, messageId: String, text: String, replyToId: String? = null): RoomMessage
    /** Adds the reaction if I haven't used it on this message, otherwise removes it. */
    suspend fun toggleReaction(message: RoomMessage, emoji: String)
    suspend fun sendVoice(roomId: String, messageId: String, audio: ByteArray, durationMs: Long, waveform: List<Float>): RoomMessage
    suspend fun editMessage(message: RoomMessage, newText: String): RoomMessage
    suspend fun deleteMessage(message: RoomMessage)
    /** Downloads a private voice clip (only chat members can). */
    suspend fun downloadVoice(path: String): ByteArray

    /** Live connection to one room: messages, edits, deletes, read markers, typing. */
    fun openRoomConnection(roomId: String): RoomConnection

    // Push notifications
    suspend fun registerPushToken(token: String)
    /** Speaky Hub summary notifications (at most twice a day). */
    suspend fun hubNotificationsEnabled(): Boolean
    suspend fun setHubNotifications(enabled: Boolean)

    // Admin moderation (only works for accounts in private.community_admins)
    suspend fun isAdmin(): Boolean
    /** Ad-free granted on the server (admins, or listed in ad_free_users). Null when it couldn't be checked. */
    suspend fun isAdFree(): Boolean?
    suspend fun adminReports(status: String, pageSize: Int, after: AdminReport?): List<AdminReport>
    suspend fun adminSetReportStatus(reportId: String, status: String)
    suspend fun adminBan(userId: String, reason: String, removeMessages: Boolean)
    suspend fun adminUnban(userId: String)
    /** Admins: delete anyone's message (Hub or private chat). */
    suspend fun adminDeleteMessage(messageId: String)
    /** Whether [userId] is banned (admins see Ban or Unban on their profile). */
    suspend fun isUserBanned(userId: String): Boolean
    /** The owner (not a moderator): only the owner adds or removes moderators. */
    suspend fun isCommunityOwner(): Boolean
    suspend fun adminModerators(): List<CommunityUser>
    suspend fun adminSetModerator(userId: String, on: Boolean)
    /** Every private chat, newest first (read-only moderation). */
    suspend fun adminPrivateChats(pageSize: Int, after: AdminChat?): List<AdminChat>
    /** A private chat's messages, newest first; [before] pages back. */
    suspend fun adminChatMessages(roomId: String, pageSize: Int, before: AdminChatMessage?): List<AdminChatMessage>
}

interface RoomConnection {
    val newMessages: Flow<RoomMessage>
    val editedMessages: Flow<RoomMessage>
    val deletedMessageIds: Flow<String>
    /** (userId, lastReadAt epoch millis) whenever a member reads the room. */
    val readMarkers: Flow<Pair<String, Long>>
    /** A reaction was added (true) or removed (false). */
    val reactionChanges: Flow<ReactionChange>
    val typingUsers: StateFlow<List<TypingUser>>
    suspend fun connect()
    suspend fun sendTyping(isTyping: Boolean)
    suspend fun close()
}

data class ReactionChange(
    val messageId: String,
    val userId: String,
    val emoji: String,
    val added: Boolean,
)

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
        "content_blocked" in m -> "Links, contact details and offensive words aren't allowed."
        "terms_not_accepted" in m -> "Please accept the community rules first."
        "guest_not_allowed" in m || "row-level security" in m -> "Sign in with Google to chat."
        "daily_limit" in m -> "You've reached today's Speaky Hub limit. Try again tomorrow."
        "rate_limited" in m -> "You're sending messages too fast. Wait a moment."
        "last_admin" in m -> "The community needs at least one moderator."
        "not allowed" in m -> "You don't have permission to do that."
        "24 hours" in m -> "Messages can only be edited for 24 hours."
        "blocked" in m -> "You can't message this person."
        "network" in m || "unable to resolve host" in m || "timeout" in m -> "No connection. Check your internet."
        else -> fallback
    }
}

const val DAILY_MESSAGE_LIMIT = 50

/** [used] of [limit] free private messages sent in the last 24 hours; one comes back after [waitMs]. */
data class FreeMessages(val used: Int, val limit: Int, val waitMs: Long) {
    val left: Int get() = (limit - used).coerceAtLeast(0)
}

sealed interface AiCorrection {
    /** [left]: free corrections left in the last 24 hours; null for Premium. */
    data class Fixed(val text: String, val left: Int?) : AiCorrection
    data class LimitReached(val limit: Int, val waitMs: Long) : AiCorrection
    data class TooLong(val maxChars: Int) : AiCorrection
    /** Every AI provider (main and fallback) is out of free quota or busy right now. */
    data object OutOfQuota : AiCorrection
    data object Failed : AiCorrection
}
