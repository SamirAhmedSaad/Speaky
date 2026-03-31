package com.speakmind.app.feature.download.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.ai.platform.ModelDownloadState
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadUiState(
    val hasStarted: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val downloadedMB: Long = 0,
    val totalMB: Long = 0,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val waitingForWifi: Boolean = false,
)

class ModelDownloadViewModel(
    private val scenarioId: String?,
    private val modelDownloader: ModelDownloader,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        if (modelDownloader.modelExists()) {
            _uiState.value = DownloadUiState(isComplete = true)
        } else {
            // Check if a download is already running
            checkExistingDownload()
        }
    }

    private fun checkExistingDownload() {
        viewModelScope.launch {
            modelDownloader.observeDownload().collect { state ->
                if (state.isDownloading || state.progress > 0) {
                    // Download already in progress — show progress directly
                    _uiState.value = _uiState.value.copy(
                        hasStarted = true,
                        isDownloading = state.isDownloading,
                        progress = state.progress,
                        downloadedMB = state.downloadedMB,
                        totalMB = state.totalMB,
                        isComplete = state.isComplete,
                        isError = state.isError,
                        waitingForWifi = state.isDownloading && state.progress == 0 && !state.isError,
                    )

                    if (state.isComplete) {
                        navigationManager.navigate(
                            ChatDestination(scenarioId = scenarioId)
                        ) { popUpTo(0) }
                    }
                }
            }
        }
    }

    fun onStartDownload() {
        modelDownloader.startDownload()
        _uiState.value = _uiState.value.copy(hasStarted = true, isDownloading = true)

        viewModelScope.launch {
            modelDownloader.observeDownload().collect { state ->
                _uiState.value = _uiState.value.copy(
                    isDownloading = state.isDownloading,
                    progress = state.progress,
                    downloadedMB = state.downloadedMB,
                    totalMB = state.totalMB,
                    isComplete = state.isComplete,
                    isError = state.isError,
                    waitingForWifi = state.isDownloading && state.progress == 0 && !state.isError,
                )

                if (state.isComplete) {
                    // Navigate to chat
                    navigationManager.navigate(
                        ChatDestination(scenarioId = scenarioId)
                    ) {
                        // Remove download screen from back stack
                        popUpTo(0)
                    }
                }
            }
        }
    }

    fun onCancel() {
        modelDownloader.cancelDownload()
        navigationManager.back()
    }

    fun onRetry() {
        _uiState.value = _uiState.value.copy(isError = false)
        onStartDownload()
    }

    fun onContinueToChat() {
        navigationManager.navigate(
            ChatDestination(scenarioId = scenarioId)
        ) {
            popUpTo(0)
        }
    }

    fun onGoBack() {
        navigationManager.back()
    }
}
