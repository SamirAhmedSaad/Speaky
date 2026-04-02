package com.speakmind.app.feature.download.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.ai.platform.ModelDownloadState
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.feature.ai.platform.ModelPreloader
import com.speakmind.app.navigation.AiSetupDestination
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
    val errorMessage: String? = null,
    val waitingForWifi: Boolean = false,
)

class ModelDownloadViewModel(
    private val scenarioId: String?,
    private val modelDownloader: ModelDownloader,
    private val navigationManager: NavigationManager,
    private val database: SpeakyDatabase,
    private val modelPreloader: ModelPreloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        if (modelDownloader.modelExists()) {
            // Model already downloaded — go straight to chat
            navigateToChat()
        } else {
            checkExistingDownload()
        }
    }

    private fun checkExistingDownload() {
        viewModelScope.launch {
            modelDownloader.observeDownload().collect { state ->
                when {
                    state.isDownloading -> {
                        _uiState.value = _uiState.value.copy(
                            hasStarted = true,
                            isDownloading = true,
                            progress = state.progress,
                            downloadedMB = state.downloadedMB,
                            totalMB = state.totalMB,
                            isComplete = false,
                            isError = false,
                            waitingForWifi = state.isWaitingForWifi,
                        )
                    }
                    state.isComplete && modelDownloader.modelExists() -> {
                        database.speakMindQueries.updateAiEngine("local")
                        modelPreloader.preload()
                        navigateToChat()
                    }
                    state.isError && !modelDownloader.modelExists() -> {
                        // Work failed permanently (e.g. WiFi dropped before retry kicked in).
                        // Re-enqueue so it resumes automatically when WiFi is available.
                        val isPermanentError = state.errorMessage?.let { msg ->
                            msg.contains("401") || msg.contains("403") ||
                            msg.contains("404") || msg.contains("410")
                        } ?: false

                        if (!isPermanentError) {
                            modelDownloader.startDownload()
                            _uiState.value = _uiState.value.copy(
                                hasStarted = true,
                                isDownloading = true,
                                isError = false,
                                waitingForWifi = true,
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isError = true,
                                errorMessage = state.errorMessage,
                            )
                        }
                    }
                }
                // If complete but file missing — stale work, ignore (show download prompt)
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
                    errorMessage = state.errorMessage,
                    waitingForWifi = state.isWaitingForWifi,
                )

                if (state.isComplete && modelDownloader.modelExists()) {
                    database.speakMindQueries.updateAiEngine("local")
                    modelPreloader.preload()
                    navigateToChat()
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
        navigateToChat()
    }

    fun onGoBack() {
        navigationManager.back()
    }

    // Pop AiSetup + ModelDownload off the stack (inclusive), then open Chat.
    // If AiSetupDestination is not in the stack the popUpTo is a no-op, which is safe.
    private fun navigateToChat() {
        navigationManager.navigate(ChatDestination(scenarioId = scenarioId)) {
            popUpTo<AiSetupDestination> { inclusive = true }
            launchSingleTop = true
        }
    }
}
