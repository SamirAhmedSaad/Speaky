package com.speakmind.app.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.speakmind.app.ads.AdConfig
import com.speakmind.app.ads.AdManager
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.banner.LevelPlayBannerAdView
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener
import io.github.aakira.napier.Napier

@Composable
actual fun BannerAdView(modifier: Modifier) {
    if (!AdConfig.adsEnabled || !AdConfig.showBannerAds || !AdConfig.adsReady) return
    if (AdConfig.BANNER_AD_UNIT_ID.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                val config = LevelPlayBannerAdView.Config.Builder()
                    .setAdSize(LevelPlayAdSize.BANNER)
                    .build()
                LevelPlayBannerAdView(context, AdConfig.BANNER_AD_UNIT_ID, config).apply {
                    setBannerListener(object : LevelPlayBannerAdViewListener {
                        override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                            Napier.d { "Banner ad loaded" }
                        }

                        override fun onAdLoadFailed(error: LevelPlayAdError) {
                            Napier.w { "Banner ad failed: ${error.errorMessage}" }
                        }
                    })
                    loadAd()
                }
            },
            // Leaving the screen must free the banner, or it keeps refreshing in the background.
            onRelease = { it.destroy() },
        )
    }
}

@Composable
actual fun rememberInterstitialAdState(): InterstitialAdState {
    val context = LocalContext.current
    return remember {
        InterstitialAdState(context)
    }
}

actual class InterstitialAdState(
    private val context: android.content.Context,
) {
    actual fun show(onDismissed: () -> Unit) {
        if (!AdConfig.adsEnabled) {
            onDismissed()
            return
        }
        val activity = context as? Activity
        val adManager = AdManager.instance
        if (activity != null && adManager != null) {
            adManager.showInterstitial(activity, onDismissed)
        } else {
            Napier.w { "Cannot show interstitial: activity=${activity != null}, adManager=${adManager != null}" }
            onDismissed()
        }
    }
}
