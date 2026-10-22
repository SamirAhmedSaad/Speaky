package com.speakmind.app.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Subscription plans: base plan ids of the "premium" subscription in Play Console. */
enum class PremiumPlan(val basePlanId: String) { MONTHLY("monthly"), YEARLY("yearly") }

data class PlanPrice(val formatted: String, val micros: Long)

/**
 * A full-screen ad is about to show: the user picks "remove ads" or "watch the ad".
 * [unlockLesson]: the opt-in "watch a video to unlock this lesson" variant, where closing the
 * dialog just stays on the list ([onCancel]).
 */
class AdChoice(
    val onWatchAd: () -> Unit,
    val onContinue: () -> Unit,
    val unlockLesson: Boolean = false,
    val onCancel: () -> Unit = {},
)

/**
 * Speaky Premium (no ads). The platform billing code fills [active] and [prices] and sets [purchase];
 * the UI only reads this object.
 */
object Premium {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _prices = MutableStateFlow<Map<PremiumPlan, PlanPrice>>(emptyMap())
    /** Localized prices from Google Play; empty while unknown or when billing is unavailable. */
    val prices: StateFlow<Map<PremiumPlan, PlanPrice>> = _prices.asStateFlow()

    private val _adChoice = MutableStateFlow<AdChoice?>(null)
    val adChoice: StateFlow<AdChoice?> = _adChoice.asStateFlow()

    /** Opens the store's purchase sheet for a plan. Null where billing isn't available. */
    var purchase: ((PremiumPlan) -> Unit)? = null

    val canBuy: Boolean get() = purchase != null && _prices.value.isNotEmpty()

    fun setActive(value: Boolean) {
        _active.value = value
        // Bought while the "remove ads" dialog was open: skip that ad.
        if (value) _adChoice.value?.let { choice ->
            _adChoice.value = null
            choice.onContinue()
        }
    }

    fun setPrices(value: Map<PremiumPlan, PlanPrice>) {
        _prices.value = value
    }

    fun askBeforeAd(choice: AdChoice) {
        _adChoice.value = choice
    }

    /** "Watch ad". */
    fun watchAd() {
        val choice = _adChoice.value ?: return
        _adChoice.value = null
        choice.onWatchAd()
    }

    /** Back / outside tap: a forced ad still plays; an unlock offer is just declined. */
    fun dismissChoice() {
        val choice = _adChoice.value ?: return
        if (!choice.unlockLesson) return watchAd()
        _adChoice.value = null
        choice.onCancel()
    }
}
