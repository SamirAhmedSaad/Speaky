package com.speakmind.app.update

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateDecisionTest {
    private fun decide(
        current: Long = 56,
        min: Long? = null,
        optionalOn: Boolean = true,
        play: Boolean = true,
        immediate: Boolean = true,
        flexible: Boolean = true,
        enabled: Boolean = true,
    ) = decideUpdate(enabled, current, min, optionalOn, play, immediate, flexible)

    @Test fun noUpdateOnPlayNeverPromptsEvenIfServerRequiresIt() =
        assertEquals(UpdateDecision.None, decide(min = 60, play = false))

    @Test fun belowMinimumIsRequired() = assertEquals(UpdateDecision.Required, decide(min = 57))

    @Test fun atMinimumIsOnlyOptional() = assertEquals(UpdateDecision.Optional, decide(min = 56))

    @Test fun noServerMinimumIsOptional() = assertEquals(UpdateDecision.Optional, decide(min = null))

    @Test fun zeroMinimumMeansOff() = assertEquals(UpdateDecision.Optional, decide(min = 0))

    @Test fun optionalSwitchedOffShowsNothing() = assertEquals(UpdateDecision.None, decide(optionalOn = false))

    @Test fun requiredStillWinsWhenOptionalIsOff() = assertEquals(UpdateDecision.Required, decide(min = 57, optionalOn = false))

    @Test fun requiredFallsBackToOptionalWhenPlayRefusesImmediate() =
        assertEquals(UpdateDecision.Optional, decide(min = 57, immediate = false))

    @Test fun nothingWhenPlayAllowsNoFlow() =
        assertEquals(UpdateDecision.None, decide(min = 57, immediate = false, flexible = false))

    @Test fun switchedOffByAdminShowsNothingEvenIfRequired() =
        assertEquals(UpdateDecision.None, decide(min = 60, enabled = false))
}
