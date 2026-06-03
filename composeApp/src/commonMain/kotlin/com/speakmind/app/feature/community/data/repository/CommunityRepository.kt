package com.speakmind.app.feature.community.data.repository

import com.speakmind.app.feature.community.data.model.ChannelMessage
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
    suspend fun updateLastSeen()
    suspend fun checkAndIncrementDailyQuota(): Boolean

    // Global channel
    suspend fun loadChannelPage(pageSize: Int, beforeTimestampSeconds: Long?): List<ChannelMessage>
    fun observeNewChannelMessages(afterTimestampSeconds: Long): Flow<ChannelMessage>
    suspend fun sendChannelMessage(text: String): ChannelMessage?
    suspend fun syncPendingChannelMessages()
    suspend fun updateUserName(name: String)
}

const val DAILY_MESSAGE_LIMIT = 50
