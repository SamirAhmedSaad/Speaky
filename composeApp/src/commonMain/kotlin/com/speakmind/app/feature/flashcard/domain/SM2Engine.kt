package com.speakmind.app.feature.flashcard.domain

import kotlin.math.max

data class SM2Result(
    val nextIntervalDays: Double,
    val easeFactor: Double,
    val repetitions: Long,
)

object SM2Engine {
    /**
     * SM-2 spaced repetition algorithm.
     * @param quality 0-5 rating (0=blackout, 5=perfect)
     * @param repetitions current number of successful repetitions
     * @param easeFactor current ease factor (minimum 1.3)
     * @param intervalDays current interval in days
     * @return updated SM2Result with new interval, ease factor, and repetitions
     */
    /** "Again": the card comes back in 10 minutes (in days). */
    const val AGAIN_DAYS = 10.0 / (24 * 60)

    /**
     * SM-2 style spaced repetition with distinct buttons:
     * - Again (q < 3): back in 10 minutes, repetitions reset.
     * - Hard (3): 1 day, then 3, then a slow ×1.2 growth.
     * - Good (4): 2 days, then 6, then × ease factor.
     * - Easy (5): 4 days, then 8, then × ease factor × 1.3.
     */
    fun review(
        quality: Int,
        repetitions: Long,
        easeFactor: Double,
        intervalDays: Double,
    ): SM2Result {
        val q = quality.coerceIn(0, 5)
        if (q < 3) return SM2Result(nextIntervalDays = AGAIN_DAYS, easeFactor = max(1.3, easeFactor - 0.2), repetitions = 0)
        val newEF = max(1.3, easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
        val newReps = repetitions + 1
        val last = intervalDays.coerceAtLeast(1.0)
        val newInterval = when (q) {
            3 -> when (newReps) { 1L -> 1.0; 2L -> 3.0; else -> last * 1.2 }
            4 -> when (newReps) { 1L -> 2.0; 2L -> 6.0; else -> last * newEF }
            else -> when (newReps) { 1L -> 4.0; 2L -> 8.0; else -> last * newEF * 1.3 }
        }.coerceAtMost(180.0)
        return SM2Result(nextIntervalDays = newInterval, easeFactor = newEF, repetitions = newReps)
    }

    fun qualityFromRating(rating: String): Int = when (rating) {
        "again" -> 0
        "hard" -> 3
        "good" -> 4
        "easy" -> 5
        else -> 3
    }
}
