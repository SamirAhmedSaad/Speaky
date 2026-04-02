package com.speakmind.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun BannerAdView(modifier: Modifier = Modifier)

@Composable
expect fun rememberInterstitialAdState(): InterstitialAdState

expect class InterstitialAdState {
    fun show(onDismissed: () -> Unit = {})
}
