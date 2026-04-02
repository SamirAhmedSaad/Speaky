package com.speakmind.app.feature.learning.domain

import com.speakmind.app.db.SpeakyDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class StreakTracker(private val database: SpeakyDatabase) {

    fun updateStreak() {
        database.speakMindQueries.insertDefaultProgress()
        val progress = database.speakMindQueries.selectProgress().executeAsOneOrNull() ?: return
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val lastActive = progress.last_active_date

        if (lastActive == today) return // Already updated today

        val newStreak = if (isConsecutiveDay(lastActive, today)) {
            progress.streak_days + 1
        } else {
            1
        }

        database.speakMindQueries.updateStreak(newStreak, today)
    }

    fun getCurrentStreak(): Long {
        val progress = database.speakMindQueries.selectProgress().executeAsOneOrNull()
        return progress?.streak_days ?: 0
    }

    private fun isConsecutiveDay(last: String, today: String): Boolean {
        if (last.isEmpty()) return false
        return try {
            val lastDate = LocalDate.parse(last)
            val todayDate = LocalDate.parse(today)
            todayDate.toEpochDays() - lastDate.toEpochDays() == 1
        } catch (_: Exception) {
            false
        }
    }
}
