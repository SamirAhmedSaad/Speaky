package com.speakmind.app.ui.components

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.speakmind.app.ads.AdConfig
import com.speakmind.app.ads.AdManager
import com.speakmind.app.premium.Premium
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.aakira.napier.Napier

@Composable
actual fun BannerAdView(modifier: Modifier) {
    val premium by Premium.active.collectAsState()
    if (!AdConfig.adsEnabled || !AdConfig.showBannerAds || premium) return
    DisposableEffect(Unit) {
        AdConfig.bannerRequests++
        onDispose { AdConfig.bannerRequests-- }
    }
    // The app banner floats at the bottom (AppBannerHost); keep this screen's content clear of it.
    Spacer(modifier.fillMaxWidth().height(if (AdConfig.adsReady && AdConfig.bannerLoaded) 50.dp else 0.dp))
}

@Composable
actual fun AppBannerHost(modifier: Modifier) {
    val premium by Premium.active.collectAsState()
    if (!AdConfig.adsEnabled || !AdConfig.showBannerAds || !AdConfig.adsReady || premium) return
    val activity = LocalContext.current as? Activity ?: return
    val manager = AdManager.instance ?: return
    val visible = AdConfig.bannerRequests > 0 && AdConfig.bannerLoaded
    // Stays attached for the whole session so the loaded ad is never lost (LevelPlay blanks a
    // banner that is moved between views); it is only hidden on screens without a banner.
    AndroidView(
        modifier = modifier.fillMaxWidth().height(if (visible) 50.dp else 0.dp),
        factory = { context -> FrameLayout(context).also { manager.attachBannerHost(it, activity) } },
        update = { host -> host.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
        onRelease = { manager.detachBannerHost(it) },
    )
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

    actual fun showBeforeLesson(onDismissed: () -> Unit) {
        val activity = context as? Activity
        val adManager = AdManager.instance
        if (AdConfig.adsEnabled && activity != null && adManager != null) {
            adManager.showLessonInterstitial(activity, onDismissed)
        } else {
            onDismissed()
        }
    }
}
