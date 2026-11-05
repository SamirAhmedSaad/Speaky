package com.speakmind.app.feature.community.ui.components

import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.FreeMessages
import com.speakmind.app.premium.Paywall
import com.speakmind.app.premium.Premium

/**
 * Private chats and the People/Online page: open for Premium, and for free users while they have free
 * private messages left (counted on the server, last 24 hours). Otherwise the subscribe screen with a
 * countdown. Offline the check is skipped; sending checks again.
 */
suspend fun CommunityRepository.openPrivateChat(title: String, open: () -> Unit) {
    if (Premium.active.value) return open()
    // Not signed in: no free allowance to count, so no free look at who's online.
    if (isGuest() || currentUserId() == null) {
        return Premium.requirePremium(title, "Sign in with Google to get free private messages every day, or subscribe to Speaky Premium.", open)
    }
    val free = freePrivateMessages()
    if (free == null || free.left > 0) return open()
    Premium.showPaywall(freeMessagesPaywall(title, free, open))
}

fun freeMessagesPaywall(title: String, free: FreeMessages, onUnlocked: () -> Unit) = Paywall(
    title = title,
    message = "You get ${free.limit} free private messages every 24 hours. Subscribe for unlimited private chat " +
        "and to see who's online, or come back when the timer ends.",
    waitMs = free.waitMs,
    freeThing = "free messages",
    emoji = "💬",
    onUnlocked = onUnlocked,
)
