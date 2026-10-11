package com.speakmind.app.feature.community.data.model

data class CommunityUser(
    val uid: String,
    val nickname: String,
    val gender: String,
    /** Epoch millis of the last heartbeat. */
    val lastSeen: Long,
    val photoUrl: String? = null,
    /** CEFR level from the learner's app progress, e.g. "B1". */
    val level: String = "",
)

data class CommunityLocalProfile(
    val uid: String,
    val nickname: String,
    val gender: String,
    val photoUrl: String? = null,
)
