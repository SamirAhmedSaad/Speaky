package com.speakmind.app.ui.components

import android.app.Activity
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.speakmind.app.ads.AdConfig
import com.speakmind.app.ads.AdManager
import io.github.aakira.napier.Napier

@Composable
actual fun BannerAdView(modifier: Modifier) {
    if (!AdConfig.adsEnabled || !AdConfig.showBannerAds || !AdConfig.adsReady) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdConfig.BANNER_AD_UNIT_ID
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Napier.d { "Banner ad loaded" }
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Napier.w { "Banner ad failed: ${error.message}" }
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            },
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
