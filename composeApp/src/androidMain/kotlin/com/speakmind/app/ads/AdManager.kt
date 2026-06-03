package com.speakmind.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import io.github.aakira.napier.Napier

class AdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun initialize() {
        // Set max ad content rating to G (General) — blocks mature content
        val requestConfig = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build()
        MobileAds.setRequestConfiguration(requestConfig)

        MobileAds.initialize(context) {
            Napier.d { "AdMob SDK initialized" }
            AdConfig.adsReady = true
            loadInterstitial()
        }
    }

    fun loadInterstitial() {
        if (!AdConfig.adsEnabled || isLoading || interstitialAd != null) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    Napier.d { "Interstitial ad loaded" }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    Napier.w { "Interstitial ad failed to load: ${error.message}" }
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad == null) {
            Napier.d { "Interstitial not ready, skipping" }
            onDismissed()
            loadInterstitial()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial()
                onDismissed()
            }
        }

        ad.show(activity)
    }

    companion object {
        @Volatile
        var instance: AdManager? = null
            private set

        fun init(context: Context): AdManager {
            return AdManager(context).also {
                instance = it
                it.initialize()
            }
        }
    }
}
