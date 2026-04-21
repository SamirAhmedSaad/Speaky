package com.speakmind.app.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { NAME, LEVEL }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.NAME,
    val name: String = "",
    val isNameValid: Boolean = false,
    val selectedLevel: String = "",
)

class OnboardingViewModel(
    private val navigationManager: NavigationManager,
    private val database: SpeakyDatabase,
    private val themeManager: ThemeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            isNameValid = name.trim().length >= 2,
        )
    }

    fun onNameSubmitted() {
        if (_uiState.value.name.trim().length < 2) return
        _uiState.value = _uiState.value.copy(step = OnboardingStep.LEVEL)
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
