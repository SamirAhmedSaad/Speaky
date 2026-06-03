package com.speakmind.app.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AdConfig {

    // Google's official test ad unit IDs — safe to click during development
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    // Real ad unit IDs — only used in release builds
    private const val REAL_BANNER = "ca-app-pub-5111292806345357/4387009789"
    private const val REAL_INTERSTITIAL = "ca-app-pub-5111292806345357/5532570499"

    var adsEnabled: Boolean = true
    var useTestAds: Boolean = false
    var showBannerAds: Boolean = true
    // Set to true only after consent is gathered and AdMob SDK is initialized.
    // Compose-observable so BannerAdView recomposes and loads the ad automatically.
    var adsReady: Boolean by mutableStateOf(false)

    val BANNER_AD_UNIT_ID: String
        get() = if (useTestAds) TEST_BANNER else REAL_BANNER

    val INTERSTITIAL_AD_UNIT_ID: String
        get() = if (useTestAds) TEST_INTERSTITIAL else REAL_INTERSTITIAL
}
