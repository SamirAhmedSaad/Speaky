package com.speakmind.app.feature.community.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

@Composable
actual fun rememberGoogleSignIn(webClientId: String): suspend () -> GoogleIdResult {
    val context = LocalContext.current
    return remember(context, webClientId) {
        {
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = MessageDigest.getInstance("SHA-256")
                .digest(rawNonce.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val option = GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(hashedNonce)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = try {
                CredentialManager.create(context).getCredential(context, request)
            } catch (e: GetCredentialCancellationException) {
                throw GoogleSignInCancelled()
            }
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdResult(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else {
                error("Unexpected credential type")
            }
        }
    }
}
