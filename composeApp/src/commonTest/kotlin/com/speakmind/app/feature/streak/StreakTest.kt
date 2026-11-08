package com.speakmind.app.feature.streak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreakTest {
    private fun days(start: StreakState, n: Int): Pair<StreakState, List<StreakEvent>> {
        var s = start
        val events = mutableListOf<StreakEvent>()
        repeat(n) { val (ns, e) = Streak.advance(s, 1); s = ns; e?.let(events::add) }
        return s to events
    }

    @Test fun firstVisitStartsWeek() {
        val (s, e) = Streak.advance(StreakState(), null)
        assertEquals(StreakState(streak = 1, stage = 0, inStage = 1), s); assertEquals(null, e)
    }

    @Test fun sameDayChangesNothing() {
        val s = StreakState(streak = 3, inStage = 3)
        assertEquals(s to null, Streak.advance(s, 0))
    }

    @Test fun weekThenTwoWeeksThenMonthRepeats() {
        val (afterWeek, e1) = days(StreakState(streak = 1, inStage = 1), 6)
        assertEquals(StreakEvent.Completed(0, 7), e1.single()); assertEquals(1, afterWeek.stage); assertEquals(0, afterWeek.inStage)
        val (afterTwo, e2) = days(afterWeek, 14)
        assertEquals(StreakEvent.Completed(1, 21), e2.single()); assertEquals(2, afterTwo.stage)
        val (afterMonth, e3) = days(afterTwo, 30)
        assertEquals(StreakEvent.Completed(2, 51), e3.single()); assertEquals(2, afterMonth.stage); assertEquals(0, afterMonth.inStage)
    }

    @Test fun oneMissedDayIsForgivenOnce() {
        val s = StreakState(streak = 4, stage = 1, inStage = 4)
        val (saved, e) = Streak.advance(s, 2)
        assertEquals(StreakEvent.Saved(5), e); assertEquals(5, saved.streak); assertEquals(1, saved.stage); assertTrue(saved.graceUsed)
        val (reset, e2) = Streak.advance(saved, 2)
        assertEquals(StreakEvent.Reset(5), e2); assertEquals(StreakState(streak = 1, stage = 0, inStage = 1), reset)
    }

    @Test fun twoMissedDaysResetToWeek() {
        val (s, e) = Streak.advance(StreakState(streak = 20, stage = 2, inStage = 6), 3)
        assertEquals(StreakEvent.Reset(20), e); assertEquals(StreakState(streak = 1, stage = 0, inStage = 1), s)
    }

    @Test fun finishingAChallengeGivesTheChanceBack() {
        val start = StreakState(streak = 5, stage = 0, inStage = 5, graceUsed = true)
        val (after, events) = days(start, 2)
        assertTrue(events.single() is StreakEvent.Completed); assertEquals(false, after.graceUsed)
    }
}
