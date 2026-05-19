package com.speakmind.app.feature.community.data.repository

import com.speakmind.app.feature.community.data.model.ChatMessage
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import kotlinx.coroutines.flow.Flow

interface CommunityRepository {
    fun currentUserId(): String?
    suspend fun signInAnonymously(): String
    suspend fun saveUserProfile(nickname: String, gender: String)
    suspend fun getLocalProfile(): CommunityLocalProfile?
    suspend fun getUserNickname(): String
    fun getUsers(searchQuery: String = "", lastUid: String? = null): Flow<List<CommunityUser>>
    fun getMessages(chatId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(chatId: String, text: String)
    suspend fun updateLastSeen()
    suspend fun syncPendingMessages()
    fun getTotalUnreadCount(): Flow<Int>
    fun getUnreadCounts(): Flow<Map<String, Int>>
    suspend fun markChatRead(chatId: String)
    suspend fun checkAndIncrementDailyQuota(): Boolean  // true = message allowed, false = limit reached
    fun observeAllChatsForUnread(): Flow<Int>  // emits total unread count in real-time
}

const val DAILY_MESSAGE_LIMIT = 50

fun chatIdFor(uid1: String, uid2: String): String =
    listOf(uid1, uid2).sorted().joinToString("_")
