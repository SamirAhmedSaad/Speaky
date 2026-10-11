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

enum class AuthMode { SIGN_IN, SIGN_UP, EMAIL_CODE }

data class CommunityAuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val code: String = "",
    /** Waiting for the 6-digit code that was emailed. */
    val awaitingCode: Boolean = false,
    /** The code confirms a new email+password account (vs. a passwordless login). */
    val codeIsSignupConfirmation: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val googleEnabled: Boolean = AppConfig.googleWebClientId.isNotBlank(),
)

class CommunityAuthViewModel(
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityAuthUiState())
    val uiState: StateFlow<CommunityAuthUiState> = _uiState.asStateFlow()

    fun onModeChanged(mode: AuthMode) =
        _uiState.update { it.copy(mode = mode, error = null, info = null, awaitingCode = false, code = "") }

    fun onEmailChanged(v: String) = _uiState.update { it.copy(email = v.trim(), error = null) }
    fun onPasswordChanged(v: String) = _uiState.update { it.copy(password = v, error = null) }
    fun onConfirmPasswordChanged(v: String) = _uiState.update { it.copy(confirmPassword = v, error = null) }
    fun onCodeChanged(v: String) = _uiState.update { it.copy(code = v.filter(Char::isDigit).take(8), error = null) }

    fun onSubmit() {
        val s = _uiState.value
        if (!isValidEmail(s.email)) {
            _uiState.update { it.copy(error = "Enter a valid email address") }
            return
        }
        when {
            s.awaitingCode -> verifyCode()
            s.mode == AuthMode.SIGN_IN -> run {
                if (s.password.isEmpty()) return@run setError("Enter your password")
                launchAuth {
                    try {
                        repository.signInWithEmail(s.email, s.password)
                        onSignedIn()
                    } catch (e: Exception) {
                        if (e.message.orEmpty().contains("not confirmed", ignoreCase = true)) {
                            repository.resendSignupCode(s.email)
                            _uiState.update {
                                it.copy(
                                    awaitingCode = true,
                                    codeIsSignupConfirmation = true,
                                    info = "Please confirm your email. We sent a code to ${s.email}.",
                                )
                            }
                        } else throw e
                    }
                }
            }
            s.mode == AuthMode.SIGN_UP -> run {
                if (s.password.length < 8) return@run setError("Password must be at least 8 characters")
                if (s.password != s.confirmPassword) return@run setError("Passwords don't match")
                launchAuth {
                    val signedIn = repository.signUpWithEmail(s.email, s.password)
                    if (signedIn) {
                        onSignedIn()
                    } else {
                        _uiState.update {
                            it.copy(
                                awaitingCode = true,
                                codeIsSignupConfirmation = true,
                                info = "We sent a confirmation code to ${s.email}. Enter it below, or tap the link in the email and then sign in.",
                            )
                        }
                    }
                }
            }
            else -> launchAuth {
                repository.sendEmailCode(s.email)
                _uiState.update {
                    it.copy(awaitingCode = true, codeIsSignupConfirmation = false, info = "We sent a login code to ${s.email}.")
                }
            }
        }
    }

    private fun verifyCode() {
        val s = _uiState.value
        if (s.code.length < 6) return setError("Enter the code from the email")
        launchAuth {
            repository.verifyEmailCode(s.email, s.code, s.codeIsSignupConfirmation)
            onSignedIn()
        }
    }

    fun onResendCode() {
        val s = _uiState.value
        launchAuth {
            if (s.codeIsSignupConfirmation) repository.resendSignupCode(s.email) else repository.sendEmailCode(s.email)
            _uiState.update { it.copy(info = "A new code is on its way to ${s.email}.") }
        }
    }

    fun onGoogleSignIn(signIn: suspend () -> GoogleIdResult) {
        if (!_uiState.value.googleEnabled) return setError("Google sign-in isn't set up yet. Use email for now.")
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
        _uiState.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Napier.w("Community auth failed", e)
                _uiState.update { it.copy(error = friendlyError(e)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun setError(msg: String) = _uiState.update { it.copy(error = msg) }
}

private fun isValidEmail(email: String) =
    Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(email)

private fun friendlyError(e: Exception): String {
    val m = e.message.orEmpty().lowercase()
    return when {
        "invalid login credentials" in m -> "Wrong email or password."
        "already registered" in m || "user_already_exists" in m -> "This email already has an account. Sign in instead."
        "rate limit" in m || "over_email_send_rate_limit" in m || "security purposes" in m ->
            "Too many attempts. Please wait a minute and try again."
        "expired" in m || "invalid" in m && "otp" in m || "token has expired" in m -> "That code is wrong or expired. Request a new one."
        "provider is not enabled" in m || "unsupported provider" in m -> "This sign-in method isn't enabled yet."
        "signups not allowed" in m -> "New sign-ups are currently disabled."
        "network" in m || "unable to resolve host" in m || "timeout" in m -> "No connection. Check your internet and try again."
        else -> "Sign-in failed. Please try again."
    }
}
