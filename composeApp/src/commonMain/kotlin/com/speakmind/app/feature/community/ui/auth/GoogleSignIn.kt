package com.speakmind.app.feature.community.ui.auth

import androidx.compose.runtime.Composable

data class GoogleIdResult(val idToken: String, val rawNonce: String)

class GoogleSignInCancelled : Exception("cancelled")

/** Returns a launcher that shows the Google account picker and yields an ID token. */
@Composable
expect fun rememberGoogleSignIn(webClientId: String): suspend () -> GoogleIdResult
