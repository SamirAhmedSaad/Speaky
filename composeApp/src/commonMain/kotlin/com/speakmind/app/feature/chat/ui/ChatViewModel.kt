package com.speakmind.app.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.ai.domain.AiEngine
import com.speakmind.app.feature.ai.domain.PromptBuilder
import com.speakmind.app.feature.ai.domain.ResponseParser
import com.speakmind.app.feature.ai.platform.AiEngineProvider
import com.speakmind.app.feature.geminichat.data.InvalidApiKeyException
import com.speakmind.app.feature.geminichat.data.NoModelQuotaException
import com.speakmind.app.feature.voice.platform.SpeechRecognizerEngine
import com.speakmind.app.feature.voice.platform.SpeechResult
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.navigation.AiSetupDestination
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.Correction
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.home.data.NewsInLevelsRepository
import com.speakmind.app.feature.home.data.ScenarioRepository
import com.speakmind.app.feature.home.domain.model.Scenario
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.TtsSpeedManager
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
    val isModelLoaded: Boolean = false,
    val showEngineToggle: Boolean = false,
    val activeEngine: String = "",
)

class ChatViewModel(
    private val scenarioId: String?,
    private val navigationManager: NavigationManager,
    private val scenarioRepository: ScenarioRepository,
    private val newsInLevelsRepository: NewsInLevelsRepository,
    private val database: SpeakyDatabase,
    private val aiEngineProvider: AiEngineProvider,
    private val speechRecognizer: SpeechRecognizerEngine,
    private val ttsEngine: TextToSpeechEngine,
    private val ttsSpeedManager: TtsSpeedManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeEngine: AiEngine? = null

    /** Tracks whether the last user message was sent via voice */
    private var lastMessageWasVoice = false

    init {
        loadScenarioAndResolveEngine()
        collectSpeechResults()
    }

    private fun loadScenarioAndResolveEngine() {
        viewModelScope.launch {
            database.speakMindQueries.insertDefaultProgress()
            val progress = database.speakMindQueries.selectProgress().executeAsOneOrNull()
            val level = progress?.level ?: "A2"

            if (scenarioId != null) {
                val scenario = scenarioRepository.getScenarioById(scenarioId)
                    ?: newsInLevelsRepository.getTopicById(scenarioId)
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

            resolveEngine()
        }
    }

    private suspend fun resolveEngine() {
        val engine = aiEngineProvider.getActiveEngine()
        if (engine == null) {
            navigationManager.navigate(AiSetupDestination(scenarioId = scenarioId)) {
                popUpTo<ChatDestination> { inclusive = true }
            }
            return
        }
        activeEngine = engine
        val canToggle = aiEngineProvider.hasGeminiKey() && aiEngineProvider.hasLocalModel()
        _uiState.value = _uiState.value.copy(
            isModelLoaded = true,
            showEngineToggle = canToggle,
            activeEngine = engine.engineType,
        )
    }

    fun onSwitchEngine() {
        viewModelScope.launch {
            val next = if (_uiState.value.activeEngine == "gemini_api") "local" else "gemini_api"
            database.speakMindQueries.updateAiEngine(next)
            resolveEngine()
        }
    }

    /** Called when Chat screen resumes (e.g. returning from AiSetup). Re-resolves the active engine. */
    fun onResumed() {
        if (!_uiState.value.isModelLoaded) return // still initialising — init handles it
        viewModelScope.launch { resolveEngine() }
    }

    fun onOpenSettings() {
        navigationManager.navigate(AiSetupDestination(scenarioId = scenarioId)) {
            launchSingleTop = true
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSendMessage(fromVoice: Boolean = false) {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return

        lastMessageWasVoice = fromVoice

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
            val engine = activeEngine

            if (engine == null || !state.isModelLoaded) {
                val msg = ChatMessage(
                    id = "ai_${state.messages.size}",
                    role = MessageRole.ASSISTANT,
                    content = "AI engine not ready. Please go back and set up your AI.",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                _uiState.value = state.copy(messages = state.messages + msg, isGenerating = false)
                return@launch
            }

            try {
                val reply = engine.chat(
                    messages = state.messages,
                    userLevel = state.userLevel,
                    scenario = state.scenario,
                )

                val parsed = ResponseParser.parse(reply)

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

                if (lastMessageWasVoice) {
                    speakAiResponse(parsed.reply)
                }

                parsed.corrections.forEach { saveCorrection(it) }

            } catch (e: InvalidApiKeyException) {
                Napier.e { "Gemini key invalid: ${e.message}" }
                val errorMsg = ChatMessage(
                    id = "ai_${state.messages.size}",
                    role = MessageRole.ASSISTANT,
                    content = "Your Gemini API key is no longer valid. Please go to Settings and enter a new key.",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                _uiState.value = _uiState.value.copy(
                    messages = state.messages + errorMsg,
                    isGenerating = false,
                )
                // Clear the bad key and engine pref so AiSetup shows the key entry form again
                aiEngineProvider.clearGeminiKey()
                database.speakMindQueries.updateAiEngine("")
                navigationManager.navigate(AiSetupDestination(scenarioId = scenarioId)) {
                    launchSingleTop = true
                }
            } catch (e: NoModelQuotaException) {
                Napier.e { "Gemini quota exceeded: ${e.message}" }
                val errorMsg = ChatMessage(
                    id = "ai_${state.messages.size}",
                    role = MessageRole.ASSISTANT,
                    content = "Your Gemini API key has run out of free quota. Please check your key in Settings.",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                _uiState.value = _uiState.value.copy(
                    messages = state.messages + errorMsg,
                    isGenerating = false,
                )
            } catch (e: Exception) {
                Napier.e { "AI generation failed: ${e.message}" }
                val errorMsg = ChatMessage(
                    id = "ai_${state.messages.size}",
                    role = MessageRole.ASSISTANT,
                    content = "Sorry, I had trouble generating a response. Please try again.",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                _uiState.value = _uiState.value.copy(
                    messages = state.messages + errorMsg,
                    isGenerating = false,
                )
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

    /** Whether the user is physically holding the mic button */
    private var isHoldingMic = false

    /**
     * Text accumulated across multiple recognizer sessions while the user holds the mic.
     * Each Final result appends here; partials are shown as accumulated + current partial.
     */
    private val accumulatedVoiceText = StringBuilder()

    /**
     * Set when the user releases the mic. We then wait for the async [SpeechResult.Final]
     * before sending, rather than reading inputText immediately (which may still be a partial).
     */
    private var pendingSendAfterVoice = false

    fun onStartRecording() {
        if (isHoldingMic) return
        if (!speechRecognizer.isAvailable()) {
            Napier.w { "Speech recognition not available on this device" }
            return
        }
        isHoldingMic = true
        pendingSendAfterVoice = false
        accumulatedVoiceText.clear()
        _uiState.value = _uiState.value.copy(isRecording = true, inputText = "")
        speechRecognizer.startListening("en-US")
    }

    fun onStopRecording() {
        if (!isHoldingMic) return
        isHoldingMic = false
        speechRecognizer.stopListening()
        _uiState.value = _uiState.value.copy(isRecording = false)

        // Don't send immediately — onResults fires asynchronously after stopListening.
        // Set a flag so collectSpeechResults sends once the Final result arrives.
        pendingSendAfterVoice = true

        // Safety timeout: if onResults never fires (e.g. recognizer error), send what we have.
        viewModelScope.launch {
            delay(3000)
            if (pendingSendAfterVoice) {
                pendingSendAfterVoice = false
                val text = accumulatedVoiceText.toString().trim()
                    .ifEmpty { _uiState.value.inputText.trim() }
                if (text.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(inputText = text)
                    onSendMessage(fromVoice = true)
                }
            }
        }
    }

    private fun collectSpeechResults() {
        viewModelScope.launch {
            speechRecognizer.results.collect { result ->
                when (result) {
                    is SpeechResult.Partial -> {
                        // Show accumulated confirmed text + in-progress partial
                        val prefix = if (accumulatedVoiceText.isEmpty()) "" else "$accumulatedVoiceText "
                        _uiState.value = _uiState.value.copy(inputText = prefix + result.text)
                    }
                    is SpeechResult.Final -> {
                        // Append this session's confirmed text
                        if (accumulatedVoiceText.isNotEmpty()) accumulatedVoiceText.append(" ")
                        accumulatedVoiceText.append(result.text)
                        _uiState.value = _uiState.value.copy(inputText = accumulatedVoiceText.toString())

                        if (isHoldingMic) {
                            // Still holding — restart for continuous dictation
                            speechRecognizer.startListening("en-US")
                        } else if (pendingSendAfterVoice) {
                            // User released mic; final text is ready — send now
                            pendingSendAfterVoice = false
                            onSendMessage(fromVoice = true)
                        }
                    }
                    is SpeechResult.Error -> {
                        Napier.w { "Speech recognition error: ${result.message}" }
                        if (isHoldingMic) {
                            speechRecognizer.startListening("en-US")
                        } else if (pendingSendAfterVoice) {
                            // Error after release — send accumulated text if any
                            pendingSendAfterVoice = false
                            val text = accumulatedVoiceText.toString().trim()
                                .ifEmpty { _uiState.value.inputText.trim() }
                            if (text.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(inputText = text)
                                onSendMessage(fromVoice = true)
                            }
                        }
                    }
                    is SpeechResult.Idle -> { /* no-op */ }
                }
            }
        }
    }

    /** Speak any message on demand (tap speaker icon) */
    fun speakMessage(text: String) {
        speakAiResponse(text)
    }

    private fun speakAiResponse(text: String) {
        viewModelScope.launch {
            try {
                ttsEngine.speak(text, rate = ttsSpeedManager.speed.value)
            } catch (e: Exception) {
                Napier.w { "TTS failed: ${e.message}" }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.stop()
        speechRecognizer.stopListening()
    }
}
