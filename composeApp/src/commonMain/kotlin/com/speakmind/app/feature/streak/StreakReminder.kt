package com.speakmind.app.feature.streak

/**
 * "Your streak is in danger" reminder: scheduled for tomorrow evening on every app open, so it only
 * fires on a day the app wasn't opened.
 */
expect object StreakReminder {
    fun scheduleTomorrow(streak: Int)
}
