package com.speakmind.app.feature.community.ui.auth

import com.speakmind.app.config.AppConfig
import com.speakmind.app.feature.community.data.repository.CommunityRepository

/** Result of tapping "Sign in with Google" anywhere in the community. */
enum class SignInOutcome {
    /** Signed in and already has a community profile: can chat right away. */
    READY,
    /** Signed in for the first time: needs to pick a nickname. */
    NEEDS_PROFILE,
    /** The user closed the Google account picker. */
    CANCELLED,
}

/** Runs the Google account picker in place and signs in to Supabase with the result. */
suspend fun signInWithGoogleInPlace(
    repository: CommunityRepository,
    launchPicker: suspend () -> GoogleIdResult,
): SignInOutcome {
    if (AppConfig.googleWebClientId.isBlank()) error("Google sign-in isn't set up yet.")
    val result = try {
        launchPicker()
    } catch (_: GoogleSignInCancelled) {
        return SignInOutcome.CANCELLED
    }
    repository.signInWithGoogle(result.idToken, result.rawNonce)
    return if (repository.getLocalProfile() != null) SignInOutcome.READY else SignInOutcome.NEEDS_PROFILE
}

/** Friendly message for a failed Google sign-in. */
fun friendlySignInError(e: Throwable): String {
    val m = e.message.orEmpty().lowercase()
    return when {
        "isn't set up" in m -> "Google sign-in isn't set up yet."
        "provider is not enabled" in m || "unsupported provider" in m -> "Google sign-in isn't enabled on the server yet."
        "no credentials" in m || "nocredential" in m -> "No Google account found on this device. Add one in Settings."
        "network" in m || "unable to resolve host" in m || "timeout" in m -> "No connection. Check your internet and try again."
        else -> "Google sign-in failed. Please try again."
    }
}
