package com.speakmind.app.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AdConfig {

    // From the Unity LevelPlay dashboard: Apps → Speaky → App Key, and Ad Units.
    // While these are empty, the app runs without ads.
    const val APP_KEY = "28be6b1ed"
    const val BANNER_AD_UNIT_ID = "0aztdd50eta7gjj4"
    const val INTERSTITIAL_AD_UNIT_ID = "nd3c7a5ua0h5fp20"
    /** "Watch a video to unlock this lesson". Empty: lessons use the interstitial instead. */
    const val REWARDED_AD_UNIT_ID = ""

    var adsEnabled: Boolean = true
    /** Debug builds: verbose LevelPlay logs and the LevelPlay test suite. Test ads are set per device in the dashboard. */
    var useTestAds: Boolean = false
    var showBannerAds: Boolean = true
    // Set to true once LevelPlay is initialized.
    // Compose-observable so BannerAdView recomposes and loads the ad automatically.
    var adsReady: Boolean by mutableStateOf(false)
    /** The shared banner has an ad to show; banner spots take their height only then. */
    var bannerLoaded: Boolean by mutableStateOf(false)
    /** How many on-screen screens want the banner (BannerAdView); the app banner shows when > 0. */
    var bannerRequests: Int by mutableIntStateOf(0)

    val isConfigured: Boolean get() = APP_KEY.isNotBlank()
}
