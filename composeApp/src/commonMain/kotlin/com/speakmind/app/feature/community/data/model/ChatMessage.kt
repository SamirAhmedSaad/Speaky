package com.speakmind.app.feature.community.data.model

/** Fixed id of the global Speaky Hub channel (seeded in Supabase). */
const val SPEAKY_HUB_ROOM_ID = "00000000-0000-0000-0000-000000000001"

enum class RoomKind { CHANNEL, GROUP, DIRECT }

enum class MessageKind { TEXT, VOICE }

data class RoomMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderNickname: String,
    val senderGender: String,
    val senderPhotoUrl: String?,
    val kind: MessageKind,
    val text: String,
    /** Public URL of the clip; null for text, or for a voice clip removed by storage cleanup. */
    val voiceUrl: String?,
    val voiceDurationMs: Long,
    /** Server timestamp in epoch millis. */
    val createdAtMillis: Long,
    /** Raw server timestamp, used as the pagination cursor. */
    val createdAtRaw: String,
    val isPending: Boolean = false,
    val isFailed: Boolean = false,
) {
    val isVoiceExpired: Boolean get() = kind == MessageKind.VOICE && voiceUrl == null && !isPending
}

data class ChatRoom(
    val id: String,
    val kind: RoomKind,
    val name: String,
    val description: String = "",
    val lastMessageAtMillis: Long = 0L,
    val lastMessagePreview: String = "",
    val memberCount: Int = 0,
    val unreadCount: Int = 0,
    val isMember: Boolean = false,
    /** The other person, for private chats. */
    val otherUser: CommunityUser? = null,
) {
    val displayName: String get() = otherUser?.nickname ?: name
}

data class TypingUser(
    val userId: String,
    val nickname: String,
)
