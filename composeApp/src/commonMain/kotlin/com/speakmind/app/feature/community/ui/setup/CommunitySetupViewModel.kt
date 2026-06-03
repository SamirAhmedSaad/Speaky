package com.speakmind.app.feature.community.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.ai.domain.NameExtractor
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.profile.domain.NameValidationResult
import com.speakmind.app.feature.profile.domain.NameValidator
import com.speakmind.app.navigation.ChannelDestination
import com.speakmind.app.navigation.CommunitySetupDestination
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommunitySetupUiState(
    val nickname: String = "",
    val gender: String = "",
    val isNicknameFromOnboarding: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

class CommunitySetupViewModel(
    private val communityRepository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunitySetupUiState())
    val uiState: StateFlow<CommunitySetupUiState> = _uiState.asStateFlow()

    init {
        checkExistingProfile()
    }

    private fun checkExistingProfile() {
        viewModelScope.launch {
            val profile = communityRepository.getLocalProfile()
            if (profile != null && profile.gender.isNotEmpty()) {
                navigationManager.navigate(ChannelDestination) {
                    popUpTo<CommunitySetupDestination> { inclusive = true }
                    launchSingleTop = true
                }
                return@launch
            }
            val savedNickname = communityRepository.getUserNickname()
            _uiState.value = _uiState.value.copy(
                nickname = savedNickname,
                isNicknameFromOnboarding = savedNickname.isNotEmpty(),
                isLoading = false,
            )
        }
    }

    fun onNicknameChanged(value: String) {
        if (value.length > 30) return
        val extracted = NameExtractor.extract(value)
        val resolved = if (extracted != null) extracted else capitalizeName(value)
        val error = if (resolved.trim().length >= 2) {
            NameValidator.errorMessage(NameValidator.validate(resolved.trim()))
        } else null
        _uiState.value = _uiState.value.copy(nickname = resolved, error = error)
    }

    fun onGenderSelected(gender: String) {
        _uiState.value = _uiState.value.copy(gender = gender, error = null)
    }

    fun onJoinClicked() {
        val state = _uiState.value
        val nickname = state.nickname.trim()
        val validationResult = NameValidator.validate(nickname)
        if (validationResult != NameValidationResult.Valid) {
            _uiState.value = state.copy(
                error = NameValidator.errorMessage(validationResult) ?: "Invalid name"
            )
            return
        }
        if (state.gender.isEmpty()) {
            _uiState.value = state.copy(error = "Please select your gender")
            return
        }
        _uiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                communityRepository.signInAnonymously()
                communityRepository.saveUserProfile(nickname, state.gender)
                navigationManager.navigate(ChannelDestination) {
                    popUpTo<CommunitySetupDestination> { inclusive = true }
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                Napier.e("CommunitySetup join failed", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Failed to join. Check your connection.",
                )
            }
        }
    }
}

private fun capitalizeName(value: String): String =
    value.split(" ").joinToString(" ") { word ->
        if (word.isEmpty()) word else word.replaceFirstChar { it.uppercase() }
    }
