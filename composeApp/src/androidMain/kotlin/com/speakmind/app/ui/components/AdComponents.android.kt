package com.speakmind.app.ui.components

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
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
import io.github.aakira.napier.Napier

@Composable
actual fun BannerAdView(modifier: Modifier) {
    if (!AdConfig.adsEnabled || !AdConfig.showBannerAds || !AdConfig.adsReady) return
    val activity = LocalContext.current as? Activity ?: return
    val banner = remember(activity) { AdManager.instance?.bannerFor(activity) } ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context -> FrameLayout(context) },
            // The same banner moves between screens: take it from wherever it is now.
            update = { container ->
                if (banner.parent !== container) {
                    (banner.parent as? ViewGroup)?.removeView(banner)
                    container.addView(banner)
                }
            },
            // Leaving the screen only detaches it; it stays loaded for the next screen.
            onRelease = { container -> container.removeView(banner) },
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
