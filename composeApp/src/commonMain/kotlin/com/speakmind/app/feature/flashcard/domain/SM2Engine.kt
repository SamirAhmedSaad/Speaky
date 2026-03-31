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
    fun review(
        quality: Int,
        repetitions: Long,
        easeFactor: Double,
        intervalDays: Double,
    ): SM2Result {
        val q = quality.coerceIn(0, 5)

        return if (q < 3) {
            // Failed review - reset
            SM2Result(
                nextIntervalDays = 1.0,
                easeFactor = max(1.3, easeFactor - 0.2),
                repetitions = 0,
            )
        } else {
            val newEF = max(
                1.3,
                easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
            )
            val newReps = repetitions + 1
            val newInterval = when (newReps) {
                1L -> 1.0
                2L -> 6.0
                else -> (intervalDays * newEF).coerceAtMost(90.0)
            }
            SM2Result(
                nextIntervalDays = newInterval,
                easeFactor = newEF,
                repetitions = newReps,
            )
        }
    }

    fun qualityFromRating(rating: String): Int = when (rating) {
        "again" -> 0
        "hard" -> 2
        "good" -> 4
        "easy" -> 5
        else -> 3
    }
}
