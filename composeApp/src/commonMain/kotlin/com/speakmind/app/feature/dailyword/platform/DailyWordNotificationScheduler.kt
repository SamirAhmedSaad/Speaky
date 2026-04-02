package com.speakmind.app.feature.dailyword.platform

expect class DailyWordNotificationScheduler {
    /**
     * Schedules the daily word notification.
     * Returns true if the app lacks exact-alarm permission and a rationale
     * dialog should be shown to the user.
     */
    fun schedule(hour: Int, minute: Int, word: String, meaning: String): Boolean
    fun cancel()
}
