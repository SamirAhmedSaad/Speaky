package com.speakmind.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import kotlinx.datetime.Clock

@Composable
expect fun BannerAdView(modifier: Modifier = Modifier)

@Composable
expect fun rememberInterstitialAdState(): InterstitialAdState

expect class InterstitialAdState {
    fun show(onDismissed: () -> Unit = {})
}

/**
 * A full-screen ad at a natural break: leaving a screen the user actually used.
 * Use the returned function instead of navigating back directly; the system back gesture is
 * handled too. Shows only after [minStayMs] on the screen (so a quick peek isn't punished);
 * app-wide frequency limits live in the platform ad manager.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberAdOnLeave(minStayMs: Long = 30_000L, onLeave: () -> Unit): () -> Unit {
    val ad = rememberInterstitialAdState()
    val enteredAt = remember { Clock.System.now().toEpochMilliseconds() }
    var leaving by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            val stayed = Clock.System.now().toEpochMilliseconds() - enteredAt
            if (stayed >= minStayMs) ad.show { onLeave() } else onLeave()
        }
    }
    BackHandler(enabled = !leaving) { leave() }
    return leave
}
