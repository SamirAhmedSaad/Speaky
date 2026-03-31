package com.speakmind.app.feature.splash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SplashUiState(
    val isModelReady: Boolean = false,
    val downloadProgress: Float = 0f,
    val statusText: String = "Preparing SpeakMind...",
    val isDownloading: Boolean = false,
    val isError: Boolean = false,
)

class SplashViewModel(
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkModelAndNavigate()
    }

    private fun checkModelAndNavigate() {
        viewModelScope.launch {
            // For now, simulate a brief splash then navigate to home
            // TODO: Check if model exists, download if needed
            _uiState.value = _uiState.value.copy(
                statusText = "Ready to learn!",
                isModelReady = true
            )
            delay(1500)
            navigationManager.clearStackAndNavigate(HomeDestination)
        }
    }

    fun retryDownload() {
        _uiState.value = _uiState.value.copy(isError = false, isDownloading = true)
        checkModelAndNavigate()
    }
}
