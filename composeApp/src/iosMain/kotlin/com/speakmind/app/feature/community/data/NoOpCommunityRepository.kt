package com.speakmind.app.feature.community.data

import com.speakmind.app.feature.community.data.model.ChatMessage
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NoOpCommunityRepository : CommunityRepository {
    override fun currentUserId(): String? = null
    override suspend fun signInAnonymously(): String = ""
    override suspend fun saveUserProfile(nickname: String, gender: String) {}
    override suspend fun getLocalProfile(): CommunityLocalProfile? = null
    override suspend fun getUserNickname(): String = ""
    override fun getUsers(searchQuery: String, lastUid: String?): Flow<List<CommunityUser>> = flowOf(emptyList())
    override fun getMessages(chatId: String): Flow<List<ChatMessage>> = flowOf(emptyList())
    override suspend fun sendMessage(chatId: String, text: String) {}
    override suspend fun updateLastSeen() {}
    override suspend fun syncPendingMessages() {}
    override fun getTotalUnreadCount(): Flow<Int> = flowOf(0)
    override fun getUnreadCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
    override suspend fun markChatRead(chatId: String) {}
    override suspend fun checkAndIncrementDailyQuota(): Boolean = true
    override fun observeAllChatsForUnread(): Flow<Int> = flowOf(0)
}
