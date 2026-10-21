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
import io.github.aakira.napier.Napier

/** Unity LevelPlay: initialization, the shared banner and the full-screen (interstitial) ad. */
class AdManager(private val context: Context) {

    private var interstitial: LevelPlayInterstitialAd? = null
    /** LevelPlay rejects a second loadAd() while one is running ("Load is already called"). */
    private var interstitialLoading = false
    private var pendingDismiss: (() -> Unit)? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    /** One full-screen ad per visit; a visit restarts when the app returns after a long break. */
    private var shownThisVisit = false
    /** After the visit ad, the next lesson opens without an ad (so the user isn't hit twice in a row). */
    private var freeLesson = false
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
                (launchContext as? Activity)?.let { bannerFor(it); placeBanner() } // preload so the first screen shows it at once
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                Napier.w { "LevelPlay init failed: ${error.errorMessage}" }
            }
        })
    }

    /** The shared banner, created for [activity] (recreated if the activity was recreated). */
    private fun bannerFor(activity: Activity): LevelPlayBannerAdView? {
        if (!AdConfig.adsReady || AdConfig.BANNER_AD_UNIT_ID.isBlank()) return null
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
        if (!AdConfig.adsEnabled) return
        val ad = interstitial ?: return
        if (!ad.isAdReady() && !interstitialLoading) {
            interstitialLoading = true
            ad.loadAd()
        }
    }

    fun onAppForeground() {
        val now = System.currentTimeMillis()
        if (backgroundSince > 0 && now - backgroundSince >= NEW_VISIT_AFTER_MS) {
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
        // Once per visit, after a minute of use.
        if (shownThisVisit || usedThisVisitMs() < FIRST_AD_AFTER_MS) {
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
        pendingDismiss = onDismissed
        shownThisVisit = true
        freeLesson = true
        ad.showAd(activity)
    }

    /**
     * Full-screen ad before a course lesson opens, every lesson - except the first one after the
     * visit ad. Also counts as this visit's ad. If none is ready the lesson simply opens.
     */
    fun showLessonInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (freeLesson) {
            freeLesson = false
            onDismissed()
            return
        }
        val ad = interstitial
        if (ad == null || !ad.isAdReady()) {
            onDismissed()
            loadInterstitial()
            return
        }
        pendingDismiss = onDismissed
        shownThisVisit = true
        ad.showAd(activity)
    }

    private fun finishShow() {
        pendingDismiss?.invoke()
        pendingDismiss = null
        loadInterstitial()
    }

    companion object {
        private const val INITIAL_RETRY_MS = 15_000L
        private const val FIRST_AD_AFTER_MS = 60_000L
        private const val NEW_VISIT_AFTER_MS = 5 * 60_000L
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
