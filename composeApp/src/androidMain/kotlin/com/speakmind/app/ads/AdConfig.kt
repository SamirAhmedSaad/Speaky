package com.speakmind.app.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AdConfig {

    // From the Unity LevelPlay dashboard: Apps → Speaky → App Key, and Ad Units.
    // While these are empty, the app runs without ads.
    const val APP_KEY = ""
    const val BANNER_AD_UNIT_ID = ""
    const val INTERSTITIAL_AD_UNIT_ID = ""

    var adsEnabled: Boolean = true
    /** Debug builds: verbose LevelPlay logs and the LevelPlay test suite. Test ads are set per device in the dashboard. */
    var useTestAds: Boolean = false
    var showBannerAds: Boolean = true
    // Set to true once LevelPlay is initialized.
    // Compose-observable so BannerAdView recomposes and loads the ad automatically.
    var adsReady: Boolean by mutableStateOf(false)

    val isConfigured: Boolean get() = APP_KEY.isNotBlank()
}
