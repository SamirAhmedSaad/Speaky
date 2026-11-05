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
    /** Dialog title for the unlock variant, e.g. "Unlock this lesson". */
    val title: String = "",
)

/**
 * A Premium-only feature was opened without Premium. [waitMs]: the free allowance (lessons, private
 * messages) is used up and comes back after this long, shown as a countdown; null for other features.
 */
class Paywall(
    val title: String,
    val message: String,
    val waitMs: Long? = null,
    /** What comes back when the countdown ends, e.g. "free lessons". */
    val freeThing: String = "free lessons",
    val emoji: String = "📚",
    val onUnlocked: () -> Unit,
)

/**
 * Speaky Premium: unlimited lessons, private chat, who's online, and no ads. The platform billing code
 * fills [active] and [prices] and sets [purchase]; the UI only reads this object.
 */
object Premium {
    /** Premium features (paid on Google Play or granted on the server). */
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** No ads: Premium, or ads switched off for everyone on the server. */
    private val _noAds = MutableStateFlow(false)
    val noAds: StateFlow<Boolean> = _noAds.asStateFlow()

    private val _paywall = MutableStateFlow<Paywall?>(null)
    val paywall: StateFlow<Paywall?> = _paywall.asStateFlow()

    /** Runs [onUnlocked] for Premium; otherwise shows the subscribe screen for this feature. */
    fun requirePremium(title: String, message: String, onUnlocked: () -> Unit) {
        if (_active.value) onUnlocked() else _paywall.value = Paywall(title, message, onUnlocked = onUnlocked)
    }

    fun showPaywall(paywall: Paywall) {
        _paywall.value = paywall
    }

    fun dismissPaywall() {
        _paywall.value = null
    }

    private val _prices = MutableStateFlow<Map<PremiumPlan, PlanPrice>>(emptyMap())
    /** Localized prices from Google Play; empty while unknown or when billing is unavailable. */
    val prices: StateFlow<Map<PremiumPlan, PlanPrice>> = _prices.asStateFlow()

    private val _adChoice = MutableStateFlow<AdChoice?>(null)
    val adChoice: StateFlow<AdChoice?> = _adChoice.asStateFlow()

    /** Opens the store's purchase sheet for a plan. Null where billing isn't available. */
    var purchase: ((PremiumPlan) -> Unit)? = null

    val canBuy: Boolean get() = purchase != null && _prices.value.isNotEmpty()

    /** A purchase sheet was opened from the app; the next activation is that purchase. */
    private var buying = false
    private val _welcome = MutableStateFlow(false)
    /** "You're Premium" celebration, shown once right after buying (not when an old purchase is restored). */
    val welcome: StateFlow<Boolean> = _welcome.asStateFlow()

    fun buy(plan: PremiumPlan) {
        val launch = purchase ?: return
        buying = true
        launch(plan)
    }

    /** Debug builds only: behave as if a purchase just went through (celebration + Premium on). */
    fun simulatePurchaseForTesting() {
        if (!com.speakmind.app.platform.isDebugMode) return
        simulated = true
        buying = true
        setActive(true)
    }

    fun dismissWelcome() {
        _welcome.value = false
    }

    private var paid = false
    private var simulated = false
    private var serverAdFree = false
    private var adsOff = false

    /** Google Play subscription state (from billing). */
    fun setActive(value: Boolean) {
        // A simulated purchase (debug builds) must survive Google Play saying there's no subscription.
        if (!value && simulated) return
        if (value && !paid && buying) {
            buying = false
            _welcome.value = true
        }
        paid = value
        publish()
    }

    /** Premium granted on the server (ad_free_users), independent of Google Play. */
    fun setServerAdFree(value: Boolean) {
        serverAdFree = value
        publish()
    }

    /** Server switch app_settings.ads_off: no ads for anyone (Premium features stay paid). */
    fun setAdsOff(value: Boolean) {
        adsOff = value
        publish()
    }

    private fun publish() {
        val value = paid || serverAdFree
        _active.value = value
        _noAds.value = value || adsOff
        // Subscribed while the subscribe screen was open: open what they were trying to reach.
        if (value) _paywall.value?.let { wall ->
            _paywall.value = null
            wall.onUnlocked()
        }
        val noAds = _noAds.value
        // Became ad-free while the "remove ads" dialog was open: skip that ad.
        if (noAds) _adChoice.value?.let { choice ->
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
