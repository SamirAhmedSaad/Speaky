package com.speakmind.app.feature.community.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.config.AppConfig
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.navigation.CommunityAuthDestination
import com.speakmind.app.navigation.CommunitySetupDestination
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityAuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val googleEnabled: Boolean = AppConfig.googleWebClientId.isNotBlank(),
)

class CommunityAuthViewModel(
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityAuthUiState())
    val uiState: StateFlow<CommunityAuthUiState> = _uiState.asStateFlow()

    fun onGoogleSignIn(signIn: suspend () -> GoogleIdResult) {
        if (!_uiState.value.googleEnabled) {
            _uiState.update { it.copy(error = "Google sign-in isn't set up yet.") }
            return
        }
        launchAuth {
            val result = try {
                signIn()
            } catch (_: GoogleSignInCancelled) {
                return@launchAuth
            }
            repository.signInWithGoogle(result.idToken, result.rawNonce)
            onSignedIn()
        }
    }

    fun onBack() = navigationManager.back()

    private fun onSignedIn() {
        navigationManager.navigate(CommunitySetupDestination) {
            popUpTo<CommunityAuthDestination> { inclusive = true }
            launchSingleTop = true
        }
    }

    private fun launchAuth(block: suspend () -> Unit) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Napier.w("Community auth failed", e)
                _uiState.update { it.copy(error = friendlyAuthError(e)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

private fun friendlyAuthError(e: Exception): String {
    val m = e.message.orEmpty().lowercase()
    return when {
        "provider is not enabled" in m || "unsupported provider" in m -> "Google sign-in isn't enabled on the server yet."
        "rate limit" in m -> "Too many attempts. Please wait a minute and try again."
        "network" in m || "unable to resolve host" in m || "timeout" in m -> "No connection. Check your internet and try again."
        else -> "Sign-in failed. Please try again."
    }
}
