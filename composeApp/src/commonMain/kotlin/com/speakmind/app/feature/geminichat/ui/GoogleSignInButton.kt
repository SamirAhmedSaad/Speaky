package com.speakmind.app.feature.geminichat.ui

import androidx.compose.runtime.Composable

@Composable
expect fun GoogleSignInButton(
    onSignedIn: (token: String, email: String) -> Unit,
    onError: (message: String) -> Unit,
)
