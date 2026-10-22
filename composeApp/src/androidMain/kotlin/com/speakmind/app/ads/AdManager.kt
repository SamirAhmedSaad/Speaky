package com.speakmind.app.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.banner.LevelPlayBannerAdView
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import io.github.aakira.napier.Napier
import com.speakmind.app.premium.AdChoice
import com.speakmind.app.premium.Premium

/** Unity LevelPlay: initialization, the shared banner and the full-screen (interstitial) ad. */
class AdManager(private val context: Context) {

    private var interstitial: LevelPlayInterstitialAd? = null
    /** LevelPlay rejects a second loadAd() while one is running ("Load is already called"). */
    private var interstitialLoading = false
    private var pendingDismiss: (() -> Unit)? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    /** One full-screen ad per visit; a visit restarts when the app returns after a long break. */
    private var shownThisVisit = false
    /** Things (lessons, People, ...) unlocked with a video this visit. */
    private val unlocked = mutableSetOf<String>()

    private var rewarded: LevelPlayRewardedAd? = null
    private var rewardedLoading = false
    private var rewardedRetryMs = INITIAL_RETRY_MS
    private var rewardEarned = false
    private var rewardedClosed = false
    private var onRewardFinished: ((Boolean) -> Unit)? = null
    private var usedMs = 0L
    private var foregroundSince = 0L
    private var backgroundSince = 0L
    private val handler = Handler(Looper.getMainLooper())

    /**
     * One banner for the whole app, loaded right after init and moved into whichever screen shows
     * a banner. A new banner per screen meant a fresh ad request (seconds) every time a screen opened.
     */
    private var banner: LevelPlayBannerAdView? = null
    private var bannerRetryMs = INITIAL_RETRY_MS
    /** Banner spots on screen, oldest first; the newest one (the screen on top) holds the banner. */
    private val bannerHosts = mutableListOf<ViewGroup>()

    fun attachBannerHost(host: ViewGroup, activity: Activity) {
        bannerHosts.remove(host)
        bannerHosts.add(host)
        bannerFor(activity)
        placeBanner()
    }

    fun detachBannerHost(host: ViewGroup) {
        bannerHosts.remove(host)
        banner?.let { if (it.parent === host) host.removeView(it) }
        placeBanner()
    }

    private fun placeBanner() {
        val view = banner ?: return
        val top = bannerHosts.lastOrNull()
        if (view.parent === top) return
        (view.parent as? ViewGroup)?.removeView(view)
        top?.addView(view)
    }

    /** [launchContext] is the activity starting the app: some ad networks need one at init. */
    fun initialize(launchContext: Context) {
        if (!AdConfig.isConfigured) {
            Napier.w { "LevelPlay app key not set; running without ads" }
            return
        }
        AdPrivacy.apply(context)
        if (AdConfig.useTestAds) LevelPlay.setAdaptersDebug(true)
        val request = LevelPlayInitRequest.Builder(AdConfig.APP_KEY).build()
        LevelPlay.init(launchContext, request, object : LevelPlayInitListener {
            override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                Napier.d { "LevelPlay initialized" }
                // Debug builds: log which ad network adapters are installed and configured.
                if (AdConfig.useTestAds) {
                    LevelPlay.validateIntegration(launchContext)
                }
                AdConfig.adsReady = true
                createInterstitial()
                createRewarded()
                (launchContext as? Activity)?.let { bannerFor(it); placeBanner() } // preload so the first screen shows it at once
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                Napier.w { "LevelPlay init failed: ${error.errorMessage}" }
            }
        })
    }

    /** The shared banner, created for [activity] (recreated if the activity was recreated). */
    private fun bannerFor(activity: Activity): LevelPlayBannerAdView? {
        if (!AdConfig.adsReady || AdConfig.BANNER_AD_UNIT_ID.isBlank() || Premium.active.value) return null
        banner?.let { existing ->
            if (existing.context === activity) return existing
            (existing.parent as? ViewGroup)?.removeView(existing)
            existing.destroy()
        }
        val config = LevelPlayBannerAdView.Config.Builder()
            .setAdSize(LevelPlayAdSize.BANNER)
            .build()
        return LevelPlayBannerAdView(activity, AdConfig.BANNER_AD_UNIT_ID, config).apply {
            setBannerListener(object : LevelPlayBannerAdViewListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    Napier.d { "Banner ad loaded" }
                    AdConfig.bannerLoaded = true
                    bannerRetryMs = INITIAL_RETRY_MS
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    Napier.w { "Banner ad failed: ${error.errorMessage}" }
                    // Retry with a fresh banner (LevelPlay ignores loadAd() on one that failed),
                    // with growing gaps, instead of waiting for someone to open another screen.
                    val failed = this@apply
                    handler.postDelayed({ if (banner === failed) replaceBanner(activity) }, bannerRetryMs)
                    bannerRetryMs = (bannerRetryMs * 2).coerceAtMost(MAX_RETRY_MS)
                }
            })
            loadAd() // refreshes itself afterwards (dashboard refresh rate)
        }.also { banner = it }
    }

    private fun replaceBanner(activity: Activity) {
        banner?.let { old ->
            (old.parent as? ViewGroup)?.removeView(old)
            old.destroy()
        }
        banner = null
        AdConfig.bannerLoaded = false
        bannerFor(activity)
        placeBanner()
    }

    private fun createInterstitial() {
        if (AdConfig.INTERSTITIAL_AD_UNIT_ID.isBlank()) return
        interstitial = LevelPlayInterstitialAd(AdConfig.INTERSTITIAL_AD_UNIT_ID).apply {
            setListener(object : LevelPlayInterstitialAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    interstitialLoading = false
                    retryDelayMs = INITIAL_RETRY_MS
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    interstitialLoading = false
                    Napier.w { "Interstitial failed to load: ${error.errorMessage}" }
                    // Back off so a no-fill doesn't turn into a request loop, but keep trying
                    // every minute until this visit's ad has shown.
                    handler.postDelayed({ loadInterstitial() }, retryDelayMs)
                    val max = if (shownThisVisit) MAX_RETRY_MS else FIRST_AD_AFTER_MS
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(max)
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    Napier.w { "Interstitial failed to show: ${error.errorMessage}" }
                    finishShow()
                }

                override fun onAdClosed(adInfo: LevelPlayAdInfo) = finishShow()
            })
        }
        loadInterstitial()
    }

    fun loadInterstitial() {
        if (!AdConfig.adsEnabled || pendingDismiss != null || onRewardFinished != null) return
        val ad = interstitial ?: return
        if (!ad.isAdReady() && !interstitialLoading) {
            interstitialLoading = true
            ad.loadAd()
        }
    }

    fun onAppForeground() {
        val now = System.currentTimeMillis()
        if (backgroundSince > 0 && now - backgroundSince >= NEW_VISIT_AFTER_MS) {
            unlocked.clear()
            shownThisVisit = false
            usedMs = 0L
        }
        foregroundSince = now
    }

    fun onAppBackground() {
        val now = System.currentTimeMillis()
        if (foregroundSince > 0) usedMs += now - foregroundSince
        foregroundSince = 0L
        backgroundSince = now
    }

    private fun usedThisVisitMs(): Long =
        usedMs + if (foregroundSince > 0) System.currentTimeMillis() - foregroundSince else 0L

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        // Once per visit, after a minute of use. Never for Premium.
        if (Premium.active.value || shownThisVisit || usedThisVisitMs() < FIRST_AD_AFTER_MS) {
            onDismissed()
            return
        }
        val ad = interstitial
        if (ad == null || !ad.isAdReady()) {
            // Not counted as shown: tried again at the next screen change.
            Napier.d { "Interstitial not ready, will try at the next screen" }
            onDismissed()
            loadInterstitial()
            return
        }
        offerPremiumThenShow(activity, onDismissed) { shownThisVisit = true }
    }

    /**
     * Full-screen ad before a course lesson opens, every lesson - except the first one after the
     * visit ad. Also counts as this visit's ad. If none is ready the lesson simply opens.
     */
    /**
     * Before opening [key] (a lesson, People, a story...): "Watch a short video to unlock"
     * (rewarded), Premium, or Not now. Free for Premium and for things already unlocked this visit.
     * With no rewarded ad available it falls back to the interstitial, or simply opens.
     */
    fun unlock(activity: Activity, key: String, title: String, everyTime: Boolean, onUnlocked: () -> Unit) {
        if (Premium.active.value || (!everyTime && key in unlocked)) return onUnlocked()
        val unlock = {
            if (!everyTime) unlocked += key
            onUnlocked()
        }
        val watch = {
            val video = rewarded
            val fallback = interstitial
            Napier.i { "Unlock $key: rewarded ready=${video?.isAdReady()} interstitial ready=${fallback?.isAdReady()}" }
            when {
                video != null && video.isAdReady() -> showRewarded(activity, video) { earned -> if (earned) unlock() }
                fallback != null && fallback.isAdReady() -> {
                    shownThisVisit = true
                    pendingDismiss = unlock
                    fallback.showAd(activity)
                }
                else -> waitForAd(activity, unlock)
            }
        }
        Premium.askBeforeAd(AdChoice(onWatchAd = watch, onContinue = unlock, unlockLesson = true, title = title))
    }

    /**
     * The user asked for a video but none is loaded: show "Loading video…" and keep trying for a
     * few seconds. If nothing arrives, unlock anyway - never block the user.
     */
    private fun waitForAd(activity: Activity, unlock: () -> Unit) {
        Premium.setLoadingAd(true)
        val start = System.currentTimeMillis()
        var lastRequest = 0L
        handler.post(object : Runnable {
            override fun run() {
                // Back pressed on "Loading video…": stay where we are.
                if (!Premium.loadingAd.value) return
                val now = System.currentTimeMillis()
                if (now - lastRequest >= RETRY_WHILE_WAITING_MS) {
                    // Ask again right away instead of waiting for the background retry delay.
                    lastRequest = now
                    loadRewarded()
                    loadInterstitial()
                }
                val video = rewarded
                val fallback = interstitial
                when {
                    video != null && video.isAdReady() -> {
                        Premium.setLoadingAd(false)
                        showRewarded(activity, video) { earned -> if (earned) unlock() }
                    }
                    fallback != null && fallback.isAdReady() -> {
                        Premium.setLoadingAd(false)
                        shownThisVisit = true
                        pendingDismiss = unlock
                        fallback.showAd(activity)
                    }
                    now - start > WAIT_FOR_AD_MS -> {
                        Premium.setLoadingAd(false)
                        Napier.w { "No video available; unlocking without one" }
                        unlock()
                    }
                    else -> handler.postDelayed(this, 300)
                }
            }
        })
    }

    private fun showRewarded(activity: Activity, ad: LevelPlayRewardedAd, onFinished: (earned: Boolean) -> Unit) {
        shownThisVisit = true
        rewardEarned = false
        rewardedClosed = false
        onRewardFinished = onFinished
        ad.showAd(activity)
    }

    /** onAdRewarded and onAdClosed can come in either order; finish once both are known. */
    private fun finishRewarded() {
        val callback = onRewardFinished ?: return
        onRewardFinished = null
        callback(rewardEarned)
        loadRewarded()
    }

    private fun createRewarded() {
        if (AdConfig.REWARDED_AD_UNIT_ID.isBlank()) return
        rewarded = LevelPlayRewardedAd(AdConfig.REWARDED_AD_UNIT_ID).apply {
            setListener(object : LevelPlayRewardedAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    rewardedLoading = false
                    rewardedRetryMs = INITIAL_RETRY_MS
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    rewardedLoading = false
                    Napier.w { "Rewarded failed to load: ${error.errorMessage}" }
                    handler.postDelayed({ loadRewarded() }, rewardedRetryMs)
                    // Users ask for these on demand: keep retrying at least every minute.
                    rewardedRetryMs = (rewardedRetryMs * 2).coerceAtMost(60_000L)
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}

                override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                    rewardEarned = true
                    if (rewardedClosed) finishRewarded()
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    Napier.w { "Rewarded failed to show: ${error.errorMessage}" }
                    // Our failure, not the user's: let them in.
                    rewardEarned = true
                    finishRewarded()
                }

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    rewardedClosed = true
                    // The reward callback may arrive just after closing.
                    if (rewardEarned) finishRewarded() else handler.postDelayed({ finishRewarded() }, 1_000)
                }
            })
        }
        loadRewarded()
    }

    private fun loadRewarded() {
        // LevelPlay rejects loads while a full-screen ad is on screen.
        if (!AdConfig.adsEnabled || Premium.active.value || pendingDismiss != null || onRewardFinished != null) return
        val ad = rewarded ?: return
        if (!ad.isAdReady() && !rewardedLoading) {
            rewardedLoading = true
            ad.loadAd()
        }
    }

    /**
     * Before a full-screen ad: "Remove ads" (subscribe) or "Watch ad". Without prices from Play
     * (billing unavailable) the ad shows directly.
     */
    private fun offerPremiumThenShow(activity: Activity, onDismissed: () -> Unit, onShown: () -> Unit) {
        val watch = {
            val ad = interstitial
            if (ad != null && ad.isAdReady()) {
                onShown()
                pendingDismiss = onDismissed
                ad.showAd(activity)
            } else {
                onDismissed()
            }
        }
        if (Premium.canBuy) Premium.askBeforeAd(AdChoice(onWatchAd = watch, onContinue = onDismissed)) else watch()
    }

    private fun finishShow() {
        val done = pendingDismiss
        pendingDismiss = null
        done?.invoke()
        loadInterstitial()
        loadRewarded()
    }

    companion object {
        private const val INITIAL_RETRY_MS = 15_000L
        private const val FIRST_AD_AFTER_MS = 60_000L
        private const val NEW_VISIT_AFTER_MS = 5 * 60_000L
        private const val WAIT_FOR_AD_MS = 15_000L
        private const val RETRY_WHILE_WAITING_MS = 2_000L
        private const val MAX_RETRY_MS = 5 * 60_000L

        @Volatile
        var instance: AdManager? = null
            private set

        /** Starts LevelPlay once per app process; later calls (e.g. the activity being recreated) reuse it. */
        @Synchronized
        fun init(context: Context): AdManager {
            instance?.let { return it }
            return AdManager(context.applicationContext).also {
                instance = it
                it.initialize(context)
            }
        }
    }
}
