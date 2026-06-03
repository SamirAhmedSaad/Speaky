package com.speakmind.app.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.ai.domain.NameExtractor
import com.speakmind.app.feature.profile.domain.NameValidationResult
import com.speakmind.app.feature.profile.domain.NameValidator
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { NAME, CONFIRM_NAME, LEVEL }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.NAME,
    val name: String = "",
    val isNameValid: Boolean = false,
    val nameError: String? = null,
    val selectedLevel: String = "",
)

class OnboardingViewModel(
    private val navigationManager: NavigationManager,
    private val database: SpeakyDatabase,
    private val themeManager: ThemeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChanged(raw: String) {
        if (raw.length > 30) return
        val trimmed = raw.trim()
        val validationResult = if (trimmed.length >= 2) NameValidator.validate(trimmed) else null
        val error = validationResult?.let { NameValidator.errorMessage(it) }
        val isValid = validationResult == NameValidationResult.Valid
        _uiState.value = _uiState.value.copy(
            name = raw,
            isNameValid = isValid,
            nameError = error,
        )
    }

    fun onNameSubmitted() {
        val raw = _uiState.value.name.trim()
        val extracted = NameExtractor.extract(raw)
        val name = if (extracted != null) extracted else raw.split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) word else word.replaceFirstChar { it.uppercase() }
        }
        val validationResult = NameValidator.validate(name.trim())
        if (validationResult != NameValidationResult.Valid) return
        _uiState.value = _uiState.value.copy(
            name = name,
            step = OnboardingStep.CONFIRM_NAME,
        )
    }

    fun onNameConfirmed() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.LEVEL)
    }

    fun onNameChangeRequested() {
        _uiState.value = _uiState.value.copy(
            step = OnboardingStep.NAME,
            name = "",
            isNameValid = false,
            nameError = null,
        )
    }

    fun onLevelSelected(level: String) {
        _uiState.value = _uiState.value.copy(selectedLevel = level)
    }

    fun onThemeToggle(isDark: Boolean) {
        themeManager.toggle(isDark)
    }

    fun onContinue() {
        val state = _uiState.value
        val name = state.name.trim()
        if (name.length < 2 || state.selectedLevel.isEmpty()) return

        viewModelScope.launch {
            database.speakMindQueries.insertDefaultProgress()
            database.speakMindQueries.updateUserName(name)
            database.speakMindQueries.updateLevel(state.selectedLevel)
            navigationManager.clearStackAndNavigate(HomeDestination)
        }
    }
}
