package com.speakmind.app.feature.community.data.model

/** Fixed id of the global Speaky Hub channel (seeded in Supabase). */
const val SPEAKY_HUB_ROOM_ID = "00000000-0000-0000-0000-000000000001"

enum class RoomKind { CHANNEL, GROUP, DIRECT }

enum class MessageKind { TEXT, VOICE }

enum class GroupRole { OWNER, ADMIN, MEMBER }

data class RoomMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderNickname: String,
    val senderGender: String,
    val senderPhotoUrl: String?,
    val kind: MessageKind,
    val text: String,
    /** Storage path of the clip; null for text, or for a clip removed by storage cleanup. */
    val voicePath: String?,
    val voiceDurationMs: Long,
    /** Loudness per bar, 0..1, recorded with the clip; empty for older clips. */
    val voiceWaveform: List<Float> = emptyList(),
    /** Server timestamp in epoch millis. */
    val createdAtMillis: Long,
    /** Raw server timestamp, used as the pagination cursor. */
    val createdAtRaw: String,
    val isEdited: Boolean = false,
    val isPending: Boolean = false,
    val isFailed: Boolean = false,
    /** The message this one replies to, if any. */
    val replyTo: ReplyPreview? = null,
    /** emoji → who reacted. */
    val reactions: Map<String, List<String>> = emptyMap(),
) {
    val isVoiceExpired: Boolean get() = kind == MessageKind.VOICE && voicePath == null && !isPending
}

/** Waveforms travel as one digit (0-9) per bar. */
fun encodeWaveform(levels: List<Float>): String? =
    levels.takeIf { it.isNotEmpty() }?.joinToString("") { (it.coerceIn(0f, 1f) * 9).toInt().toString() }

fun decodeWaveform(encoded: String?): List<Float> =
    encoded.orEmpty().mapNotNull { c -> c.digitToIntOrNull()?.let { it / 9f } }

data class ReplyPreview(
    val messageId: String,
    val senderNickname: String,
    val text: String,
    val isVoice: Boolean,
)

/** Reactions offered in the message menu. */
val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

data class ChatRoom(
    val id: String,
    val kind: RoomKind,
    val name: String,
    val description: String = "",
    val lastMessageAtMillis: Long = 0L,
    /** Exact server timestamp, the chat list's page cursor. */
    val lastMessageAtRaw: String = "",
    val lastMessagePreview: String = "",
    val memberCount: Int = 0,
    val unreadCount: Int = 0,
    val isMember: Boolean = false,
    val myRole: GroupRole? = null,
    val muted: Boolean = false,
    val isPrivate: Boolean = false,
    /** Only visible to members. */
    val inviteCode: String? = null,
    /** The other person, for private chats. */
    val otherUser: CommunityUser? = null,
) {
    val displayName: String get() = otherUser?.nickname ?: name
    val canManage: Boolean get() = myRole == GroupRole.OWNER || myRole == GroupRole.ADMIN
}

data class GroupMember(
    val user: CommunityUser,
    val role: GroupRole,
    /** Epoch millis of the member's read marker, used for "Seen". */
    val lastReadAtMillis: Long,
)

data class TypingUser(
    val userId: String,
    val nickname: String,
)

/** A private chat as seen by community admins (moderation). */
data class AdminChat(
    val roomId: String,
    val lastMessageAtMillis: Long,
    /** Exact server timestamp, the page cursor. */
    val lastMessageAtRaw: String,
    val lastMessagePreview: String,
    val messageCount: Int,
    val userA: CommunityUser,
    /** Null when the other person deleted their account. */
    val userB: CommunityUser?,
)

/** A message in a private chat, read-only for admins. */
data class AdminChatMessage(
    val id: String,
    val senderId: String,
    val isVoice: Boolean,
    val text: String,
    val voiceDurationMs: Long,
    val createdAtMillis: Long,
    /** Exact server timestamp, the page cursor. */
    val createdAtRaw: String,
    val isEdited: Boolean,
)

/** A report as seen by community admins. */
data class AdminReport(
    val id: String,
    val createdAtMillis: Long,
    /** Exact server timestamp, the page cursor. */
    val createdAtRaw: String = "",
    val reason: String,
    val details: String,
    val messageSnapshot: String,
    val status: String,
    val reporterNickname: String,
    val reportedUserId: String,
    val reportedNickname: String,
    val reportedBanned: Boolean,
    val timesReported: Int,
)
