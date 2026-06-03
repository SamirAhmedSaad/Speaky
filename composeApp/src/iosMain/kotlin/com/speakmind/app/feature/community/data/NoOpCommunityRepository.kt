package com.speakmind.app.feature.community.data

import com.speakmind.app.feature.community.data.model.ChannelMessage
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class NoOpCommunityRepository : CommunityRepository {
    override fun currentUserId(): String? = null
    override suspend fun signInAnonymously(): String = ""
    override suspend fun saveUserProfile(nickname: String, gender: String) {}
    override suspend fun getLocalProfile(): CommunityLocalProfile? = null
    override suspend fun getUserNickname(): String = ""
    override fun getUsers(searchQuery: String, lastUid: String?): Flow<List<CommunityUser>> = flowOf(emptyList())
    override suspend fun updateLastSeen() {}
    override suspend fun checkAndIncrementDailyQuota(): Boolean = true
    override suspend fun loadChannelPage(pageSize: Int, beforeTimestampSeconds: Long?): List<ChannelMessage> = emptyList()
    override fun observeNewChannelMessages(afterTimestampSeconds: Long): Flow<ChannelMessage> = emptyFlow()
    override suspend fun sendChannelMessage(text: String): ChannelMessage? = null
    override suspend fun syncPendingChannelMessages() {}
    override suspend fun updateUserName(name: String) {}
}
