package com.speakmind.app.feature.community.data.model

data class CommunityUser(
    val uid: String,
    val nickname: String,
    val gender: String,
    /** Epoch millis of the last heartbeat. */
    val lastSeen: Long,
    val photoUrl: String? = null,
)

data class CommunityLocalProfile(
    val uid: String,
    val nickname: String,
    val gender: String,
    val photoUrl: String? = null,
)
