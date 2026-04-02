package com.speakmind.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
actual fun BannerAdView(modifier: Modifier) {
    // No-op on iOS — AdMob iOS integration not yet implemented
}

@Composable
actual fun rememberInterstitialAdState(): InterstitialAdState {
    return remember { InterstitialAdState() }
}

actual class InterstitialAdState {
    actual fun show(onDismissed: () -> Unit) {
        // No-op on iOS
        onDismissed()
    }
}
