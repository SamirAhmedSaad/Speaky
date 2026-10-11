package com.speakmind.app.feature.community.data

import kotlinx.coroutines.flow.MutableStateFlow

/** The chat currently on screen, so push notifications for it can be skipped. */
object ActiveRoomTracker {
    val activeRoomId = MutableStateFlow<String?>(null)
}
