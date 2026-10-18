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
    private var pendingDismiss: (() -> Unit)? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private val startedAt = System.currentTimeMillis()
    private var lastShownAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    /**
     * One banner for the whole app, loaded right after init and moved into whichever screen shows
     * a banner. A new banner per screen meant a fresh ad request (seconds) every time a screen opened.
     */
    private var banner: LevelPlayBannerAdView? = null
    private var bannerRetryMs = INITIAL_RETRY_MS

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
                (launchContext as? Activity)?.let { bannerFor(it) } // preload so the first screen shows it at once
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                Napier.w { "LevelPlay init failed: ${error.errorMessage}" }
            }
        })
    }

    /** The shared banner, created for [activity] (recreated if the activity was recreated). */
    fun bannerFor(activity: Activity): LevelPlayBannerAdView? {
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
                    bannerRetryMs = INITIAL_RETRY_MS
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    Napier.w { "Banner ad failed: ${error.errorMessage}" }
                    // Retry (with growing gaps) instead of waiting for someone to open another screen.
                    val failed = this@apply
                    handler.postDelayed({ if (banner === failed) failed.loadAd() }, bannerRetryMs)
                    bannerRetryMs = (bannerRetryMs * 2).coerceAtMost(MAX_RETRY_MS)
                }
            })
            loadAd() // refreshes itself afterwards (dashboard refresh rate)
        }.also { banner = it }
    }

    private fun createInterstitial() {
        if (AdConfig.INTERSTITIAL_AD_UNIT_ID.isBlank()) return
        interstitial = LevelPlayInterstitialAd(AdConfig.INTERSTITIAL_AD_UNIT_ID).apply {
            setListener(object : LevelPlayInterstitialAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    retryDelayMs = INITIAL_RETRY_MS
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    Napier.w { "Interstitial failed to load: ${error.errorMessage}" }
                    // Back off so a no-fill doesn't turn into a request loop.
                    handler.postDelayed({ loadInterstitial() }, retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
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
        if (!ad.isAdReady()) ad.loadAd()
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        // Never right after opening the app, and at most one every few minutes.
        if (now - startedAt < FIRST_AD_AFTER_MS || now - lastShownAt < MIN_GAP_MS) {
            onDismissed()
            return
        }
        val ad = interstitial
        if (ad == null || !ad.isAdReady()) {
            Napier.d { "Interstitial not ready, skipping" }
            onDismissed()
            loadInterstitial()
            return
        }
        pendingDismiss = onDismissed
        lastShownAt = now
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
        private const val MIN_GAP_MS = 3 * 60_000L
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
