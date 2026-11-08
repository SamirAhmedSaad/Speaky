package com.speakmind.app.feature.streak

/**
 * Daily streak with challenges: a week (7 days), then 2 weeks (14), then a month (30), then a new month
 * each time. Missing one day gives a warning and one more chance; missing a second day in a row (or
 * using the chance twice in one challenge) starts again at the week.
 */
data class StreakState(
    /** Days in a row (opening the app). */
    val streak: Int = 0,
    /** 0 = week, 1 = two weeks, 2 = month (repeats). */
    val stage: Int = 0,
    /** Days done in the current challenge. */
    val inStage: Int = 0,
    /** The one missed day of this challenge was already forgiven. */
    val graceUsed: Boolean = false,
) {
    val goal: Int get() = goalOf(stage)
    val stageName: String get() = nameOf(stage)

    companion object {
        fun goalOf(stage: Int) = when (stage) { 0 -> 7; 1 -> 14; else -> 30 }
        fun nameOf(stage: Int) = when (stage) { 0 -> "Week challenge"; 1 -> "2-week challenge"; else -> "Month challenge" }
    }
}

sealed interface StreakEvent {
    /** A challenge was finished today: celebrate. */
    data class Completed(val stage: Int, val streak: Int) : StreakEvent
    /** Yesterday was missed; today's visit saved the streak (no chance left in this challenge). */
    data class Saved(val streak: Int) : StreakEvent
    /** The streak was lost and started again. */
    data class Reset(val lost: Int) : StreakEvent
}

object Streak {
    /**
     * The state after opening the app [daysSinceLast] days after the last visit (null = first visit).
     * Returns the new state and what to show (if anything).
     */
    fun advance(state: StreakState, daysSinceLast: Long?): Pair<StreakState, StreakEvent?> {
        if (daysSinceLast != null && daysSinceLast <= 0) return state to null
        if (daysSinceLast == null || state.streak == 0) return countDay(StreakState(), null)
        return when {
            daysSinceLast == 1L -> countDay(state, null)
            daysSinceLast == 2L && !state.graceUsed -> countDay(state.copy(graceUsed = true), StreakEvent.Saved(state.streak + 1))
            else -> countDay(StreakState(), StreakEvent.Reset(state.streak))
        }
    }

    private fun countDay(state: StreakState, event: StreakEvent?): Pair<StreakState, StreakEvent?> {
        val next = state.copy(streak = state.streak + 1, inStage = state.inStage + 1)
        if (next.inStage < next.goal) return next to event
        // Challenge done: celebrate, then the next one (the month repeats).
        return next.copy(stage = (next.stage + 1).coerceAtMost(2), inStage = 0, graceUsed = false) to
            StreakEvent.Completed(next.stage, next.streak)
    }
}
