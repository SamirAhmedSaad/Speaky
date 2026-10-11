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
    /** Server timestamp in epoch millis. */
    val createdAtMillis: Long,
    /** Raw server timestamp, used as the pagination cursor. */
    val createdAtRaw: String,
    val isEdited: Boolean = false,
    val isPending: Boolean = false,
    val isFailed: Boolean = false,
) {
    val isVoiceExpired: Boolean get() = kind == MessageKind.VOICE && voicePath == null && !isPending
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
