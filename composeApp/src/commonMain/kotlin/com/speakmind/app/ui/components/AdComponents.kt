package com.speakmind.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Marks this screen as showing the app banner, and reserves its space here. */
@Composable
expect fun BannerAdView(modifier: Modifier = Modifier)

/** The single app-wide banner, placed once at the bottom of the app (see AppRoot). */
@Composable
expect fun AppBannerHost(modifier: Modifier = Modifier)

@Composable
expect fun rememberInterstitialAdState(): InterstitialAdState

/** The full-screen ad. App-wide limits (once per visit, after a minute of use) live in the platform ad manager. */
expect class InterstitialAdState {
    fun show(onDismissed: () -> Unit = {})

    /** Before opening a course lesson (see AdManager.showLessonInterstitial). */
    fun showBeforeLesson(onDismissed: () -> Unit)
}
