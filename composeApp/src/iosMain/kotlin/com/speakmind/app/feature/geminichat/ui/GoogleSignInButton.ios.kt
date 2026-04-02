package com.speakmind.app.feature.geminichat.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import network.chaintech.sdpcomposemultiplatform.sdp

@Composable
actual fun GoogleSignInButton(
    onSignedIn: (token: String, email: String) -> Unit,
    onError: (message: String) -> Unit,
) {
    OutlinedButton(
        onClick = { onError("Google Sign-In is not supported on iOS yet.") },
        modifier = Modifier.fillMaxWidth().height(52.sdp),
        shape = RoundedCornerShape(12.sdp),
    ) {
        Text("Google Sign-In (iOS coming soon)")
    }
}
