package com.speakmind.app.feature.community.ui.auth

import androidx.compose.runtime.Composable

@Composable
actual fun rememberGoogleSignIn(webClientId: String): suspend () -> GoogleIdResult =
    { error("Google sign-in is not available on iOS yet") }
