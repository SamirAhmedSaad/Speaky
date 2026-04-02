package com.speakmind.app.feature.aisetup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.geminichat.data.ApiKeyStore
import com.speakmind.app.feature.geminichat.data.GeminiRepository
import com.speakmind.app.navigation.AiSetupDestination
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.navigation.ModelDownloadDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class AiSetupUiState(
    val geminiKeyInput: String = "",
    val isValidatingKey: Boolean = false,
    val keyError: String? = null,
    val geminiKeyIsSet: Boolean = false,
    val isChangingKey: Boolean = false,
    val localModelExists: Boolean = false,
)

class AiSetupViewModel(
    private val scenarioId: String?,
    private val navigationManager: NavigationManager,
    private val apiKeyStore: ApiKeyStore,
    private val geminiRepository: GeminiRepository,
    private val modelDownloader: ModelDownloader,
    private val database: SpeakyDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSetupUiState())
    val uiState: StateFlow<AiSetupUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            geminiKeyIsSet = apiKeyStore.hasKey(),
            localModelExists = modelDownloader.modelExists(),
        )
    }

    fun onKeyInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(geminiKeyInput = text, keyError = null)
    }

    fun onSaveGeminiKey() {
        val key = _uiState.value.geminiKeyInput.trim()
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(keyError = "Please enter your API key.")
            return
        }
        if (!key.startsWith("AI")) {
            _uiState.value = _uiState.value.copy(
                keyError = "This doesn't look like a valid Gemini key. It should start with \"AI\"."
            )
            return
        }
        _uiState.value = _uiState.value.copy(isValidatingKey = true, keyError = null)
        viewModelScope.launch {
            try {
                geminiRepository.chat(
                    apiKey = key,
                    messages = listOf(
                        ChatMessage(
                            id = "test",
                            role = MessageRole.USER,
                            content = "hi",
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                        )
                    ),
                    userLevel = "A2",
                )
                apiKeyStore.saveKey(key)
                database.speakMindQueries.updateAiEngine("gemini_api")
                _uiState.value = _uiState.value.copy(
                    geminiKeyIsSet = true,
                    isChangingKey = false,
                    isValidatingKey = false,
                    geminiKeyInput = "",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isValidatingKey = false,
                    keyError = "Key is invalid or has no internet. Please check and try again.",
                )
            }
        }
    }

    fun onChangeKey() {
        // Only show the input field — do NOT clear the stored key yet.
        // The key is replaced only when a new one is successfully validated.
        _uiState.value = _uiState.value.copy(
            isChangingKey = true,
            geminiKeyInput = "",
            keyError = null,
        )
    }

    fun onCancelChangeKey() {
        _uiState.value = _uiState.value.copy(
            isChangingKey = false,
            geminiKeyInput = "",
            keyError = null,
        )
    }

    fun onContinueWithGemini() {
        navigationManager.navigate(ChatDestination(scenarioId = scenarioId)) {
            popUpTo<AiSetupDestination> { inclusive = true }
            launchSingleTop = true
        }
    }

    fun onDownloadLocalModel() {
        viewModelScope.launch {
            database.speakMindQueries.updateAiEngine("local")
        }
        navigationManager.navigate(ModelDownloadDestination(scenarioId = scenarioId))
    }

    fun onUseLocalModel() {
        navigationManager.navigate(ChatDestination(scenarioId = scenarioId)) {
            popUpTo<AiSetupDestination> { inclusive = true }
            launchSingleTop = true
        }
    }

    fun onBack() {
        navigationManager.back()
    }
}
