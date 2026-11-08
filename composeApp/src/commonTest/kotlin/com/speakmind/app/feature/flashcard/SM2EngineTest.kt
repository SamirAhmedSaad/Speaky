package com.speakmind.app.feature.flashcard

import com.speakmind.app.feature.flashcard.domain.SM2Engine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SM2EngineTest {
    private fun next(rating: String, reps: Long = 0, ef: Double = 2.5, interval: Double = 1.0) =
        SM2Engine.review(SM2Engine.qualityFromRating(rating), reps, ef, interval)

    @Test fun newCardButtonsDiffer() {
        assertTrue(next("again").nextIntervalDays < 0.01)
        assertEquals(1.0, next("hard").nextIntervalDays)
        assertEquals(2.0, next("good").nextIntervalDays)
        assertEquals(4.0, next("easy").nextIntervalDays)
    }

    @Test fun hardIsNotAFail() {
        val r = next("hard", reps = 3, interval = 10.0)
        assertEquals(4, r.repetitions); assertTrue(r.nextIntervalDays >= 10.0)
    }

    @Test fun againResets() {
        val r = next("again", reps = 5, interval = 30.0)
        assertEquals(0, r.repetitions); assertTrue(r.nextIntervalDays < 0.01)
    }

    @Test fun secondReviewGrows() {
        assertEquals(3.0, next("hard", reps = 1).nextIntervalDays)
        assertEquals(6.0, next("good", reps = 1).nextIntervalDays)
        assertEquals(8.0, next("easy", reps = 1).nextIntervalDays)
    }
}
