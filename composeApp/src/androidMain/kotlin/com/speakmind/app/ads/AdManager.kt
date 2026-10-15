package com.speakmind.app.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import io.github.aakira.napier.Napier

/** Unity LevelPlay: initialization and the full-screen (interstitial) ad. Banners are in BannerAdView. */
class AdManager(private val context: Context) {

    private var interstitial: LevelPlayInterstitialAd? = null
    private var pendingDismiss: (() -> Unit)? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private val handler = Handler(Looper.getMainLooper())

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
                AdConfig.adsReady = true
                createInterstitial()
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                Napier.w { "LevelPlay init failed: ${error.errorMessage}" }
            }
        })
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
        val ad = interstitial
        if (ad == null || !ad.isAdReady()) {
            Napier.d { "Interstitial not ready, skipping" }
            onDismissed()
            loadInterstitial()
            return
        }
        pendingDismiss = onDismissed
        ad.showAd(activity)
    }

    private fun finishShow() {
        pendingDismiss?.invoke()
        pendingDismiss = null
        loadInterstitial()
    }

    companion object {
        private const val INITIAL_RETRY_MS = 15_000L
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
