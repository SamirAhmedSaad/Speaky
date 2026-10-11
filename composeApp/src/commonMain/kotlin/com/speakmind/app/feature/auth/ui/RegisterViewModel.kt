package com.speakmind.app.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.core.domain.model.AuthResult
import com.speakmind.app.feature.auth.domain.AuthRepository
import com.speakmind.app.feature.auth.domain.NameValidator
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val nameError: String? = null,
)

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        val validation = if (name.isNotBlank()) NameValidator.validate(name) else null
        _uiState.value = _uiState.value.copy(
            displayName = name,
            nameError = if (validation != null && !validation.isValid) validation.reason else null,
            errorMessage = null,
        )
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password, errorMessage = null)
    }

    fun onSignUp() {
        val state = _uiState.value

        val nameValidation = NameValidator.validate(state.displayName)
        if (!nameValidation.isValid) {
            _uiState.value = state.copy(nameError = nameValidation.reason)
            return
        }

        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all fields")
            return
        }

        if (state.password.length < 6) {
            _uiState.value = state.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords don't match")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signUp(
                state.email.trim(),
                state.password,
                state.displayName.trim(),
            )
            when (result) {
                is AuthResult.Success -> {
                    navigationManager.clearStackAndNavigate(HomeDestination)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun onNavigateBack() {
        navigationManager.back()
    }
}
