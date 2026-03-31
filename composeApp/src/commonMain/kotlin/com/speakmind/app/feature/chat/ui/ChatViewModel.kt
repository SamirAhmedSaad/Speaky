package com.speakmind.app.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakMindDatabase
import com.speakmind.app.feature.ai.domain.PromptBuilder
import com.speakmind.app.feature.ai.domain.ResponseParser
import com.speakmind.app.feature.ai.platform.findModelFile
import com.speakmind.app.feature.ai.platform.LlmEngine
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.Correction
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.home.data.ScenarioRepository
import com.speakmind.app.feature.home.domain.model.Scenario
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val scenario: Scenario? = null,
    val isGenerating: Boolean = false,
    val inputText: String = "",
    val isRecording: Boolean = false,
    val title: String = "Free Talk",
    val userLevel: String = "A2",
    val sessionStartTime: Long = Clock.System.now().toEpochMilliseconds(),
    val flashcardSavedToast: Boolean = false,
    val ttsSpeed: Float = 1.0f,
    val modelStatus: String = "",
    val isModelLoaded: Boolean = false,
)

class ChatViewModel(
    private val scenarioId: String?,
    private val navigationManager: NavigationManager,
    private val scenarioRepository: ScenarioRepository,
    private val database: SpeakMindDatabase,
    private val modelDownloader: ModelDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val llmEngine = LlmEngine()

    init {
        loadScenarioAndModel()
    }

    private fun loadScenarioAndModel() {
        viewModelScope.launch {
            database.speakMindQueries.insertDefaultProgress()
            val progress = database.speakMindQueries.selectProgress().executeAsOneOrNull()
            val level = progress?.level ?: "A2"

            if (scenarioId != null) {
                val scenarios = scenarioRepository.loadScenarios()
                val scenario = scenarios.find { it.id == scenarioId }
                if (scenario != null) {
                    _uiState.value = _uiState.value.copy(
                        scenario = scenario,
                        title = scenario.title,
                        userLevel = level,
                        messages = listOf(
                            ChatMessage(
                                id = "ai_0",
                                role = MessageRole.ASSISTANT,
                                content = scenario.aiOpening,
                                timestamp = Clock.System.now().toEpochMilliseconds()
                            )
                        )
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    title = "Free Talk",
                    userLevel = level,
                    messages = listOf(
                        ChatMessage(
                            id = "ai_0",
                            role = MessageRole.ASSISTANT,
                            content = "Hi there! I'm Sage, your English tutor. What would you like to talk about today? You can choose any topic - I'm here to help you practice!",
                            timestamp = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                )
            }

            // Load the LLM model
            loadModel()
        }
    }

    private suspend fun loadModel() {
        _uiState.value = _uiState.value.copy(modelStatus = "Loading AI model...")

        try {
            // Look for model in standard locations
            val modelPath = findModelPath()
            if (modelPath != null) {
                Napier.d { "Loading model from: $modelPath" }
                llmEngine.load(modelPath)
                _uiState.value = _uiState.value.copy(
                    isModelLoaded = true,
                    modelStatus = ""
                )
                Napier.d { "Model loaded successfully" }
            } else {
                _uiState.value = _uiState.value.copy(
                    modelStatus = "No model found. Place a .gguf file in /sdcard/Download/"
                )
                Napier.w { "No model file found" }
            }
        } catch (e: Exception) {
            Napier.e { "Failed to load model: ${e.message}" }
            _uiState.value = _uiState.value.copy(
                modelStatus = "Model load failed: ${e.message}"
            )
        }
    }

    private fun findModelPath(): String? {
        // First check app-internal path (WorkManager download location)
        modelDownloader.getModelPath()?.let { return it }
        // Then check external storage (manually placed files)
        return findModelFile()
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(
            id = "user_${_uiState.value.messages.size}",
            role = MessageRole.USER,
            content = text,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            isGenerating = true,
        )

        generateAiResponse()
    }

    private fun generateAiResponse() {
        viewModelScope.launch {
            val state = _uiState.value

            if (!state.isModelLoaded) {
                // No model - show a message
                val msg = ChatMessage(
                    id = "ai_${state.messages.size}",
                    role = MessageRole.ASSISTANT,
                    content = "I need an AI model to respond. Please place a .gguf model file in your Downloads folder and restart the chat.",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                _uiState.value = state.copy(
                    messages = state.messages + msg,
                    isGenerating = false,
                )
                return@launch
            }

            // Build the prompt with conversation history
            val prompt = PromptBuilder.buildConversationPrompt(
                scenario = state.scenario,
                history = state.messages,
                userLevel = state.userLevel,
            )

            // Stream tokens from LLM
            val responseBuilder = StringBuilder()
            try {
                llmEngine.generate(prompt).collect { token ->
                    responseBuilder.append(token)
                    val streamingMessage = ChatMessage(
                        id = "ai_${state.messages.size}",
                        role = MessageRole.ASSISTANT,
                        content = responseBuilder.toString(),
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        isStreaming = true,
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = state.messages + streamingMessage,
                    )
                }
            } catch (e: Exception) {
                Napier.e { "LLM generation failed: ${e.message}" }
                if (responseBuilder.isEmpty()) {
                    responseBuilder.append("Sorry, I had trouble generating a response. Please try again.")
                }
            }

            // Parse for corrections
            val parsed = ResponseParser.parse(responseBuilder.toString())

            val aiMessage = ChatMessage(
                id = "ai_${state.messages.size}",
                role = MessageRole.ASSISTANT,
                content = parsed.reply,
                corrections = parsed.corrections,
                timestamp = Clock.System.now().toEpochMilliseconds(),
            )

            _uiState.value = _uiState.value.copy(
                messages = state.messages + aiMessage,
                isGenerating = false,
            )

            // Auto-save corrections as flashcards
            parsed.corrections.forEach { correction ->
                saveCorrection(correction)
            }
        }
    }

    private fun saveCorrection(correction: Correction) {
        val now = Clock.System.now().toEpochMilliseconds()
        val oneDayMs = 86_400_000L

        database.speakMindQueries.insertFlashcard(
            word = correction.corrected,
            sentence = "Corrected from: '${correction.original}' to '${correction.corrected}'",
            context = _uiState.value.scenario?.title ?: "Free Talk",
            grammar_note = correction.explanation,
            error_type = correction.type,
            next_review = now + oneDayMs,
            interval_days = 1.0,
            ease_factor = 2.5,
            repetitions = 0,
            created_at = now,
            source_scenario_id = _uiState.value.scenario?.id,
        )
        database.speakMindQueries.incrementVocab()
    }

    fun saveWordAsFlashcard(word: String, sentence: String) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val oneDayMs = 86_400_000L

            database.speakMindQueries.insertFlashcard(
                word = word,
                sentence = sentence,
                context = _uiState.value.scenario?.title ?: "Free Talk",
                grammar_note = "",
                error_type = "",
                next_review = now + oneDayMs,
                interval_days = 1.0,
                ease_factor = 2.5,
                repetitions = 0,
                created_at = now,
                source_scenario_id = _uiState.value.scenario?.id,
            )
            database.speakMindQueries.incrementVocab()

            _uiState.value = _uiState.value.copy(flashcardSavedToast = true)
            delay(2000)
            _uiState.value = _uiState.value.copy(flashcardSavedToast = false)
        }
    }

    fun onEndConversation() {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val duration = ((now - _uiState.value.sessionStartTime) / 1000).toInt()
            val minutes = (duration / 60).toLong()

            database.speakMindQueries.insertDefaultProgress()
            database.speakMindQueries.incrementConversations(minutes)

            navigationManager.back()
        }
    }

    fun onToggleRecording() {
        _uiState.value = _uiState.value.copy(
            isRecording = !_uiState.value.isRecording
        )
    }

    override fun onCleared() {
        super.onCleared()
        llmEngine.unload()
    }
}
