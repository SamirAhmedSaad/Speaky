package com.speakmind.app.feature.community.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Who has Speaky Premium, for the gold badge. Filled from every user the app loads from the server
 * (message senders, People, profiles), so screens and cached messages need no extra field.
 */
object PremiumMembers {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun note(userId: String, premium: Boolean) {
        if ((userId in _ids.value) == premium) return
        _ids.update { if (premium) it + userId else it - userId }
    }
}
