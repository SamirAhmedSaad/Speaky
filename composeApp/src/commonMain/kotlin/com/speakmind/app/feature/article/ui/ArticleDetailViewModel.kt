package com.speakmind.app.feature.article.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.ai.platform.AiEngineProvider
import com.speakmind.app.feature.home.data.NewsInLevelsRepository
import com.speakmind.app.feature.home.data.ScenarioRepository
import com.speakmind.app.feature.home.domain.model.Scenario
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.feature.wordlookup.data.TranslationApiClient
import com.speakmind.app.feature.wordlookup.data.WiktionaryApiClient
import com.speakmind.app.navigation.AiSetupDestination
import com.speakmind.app.ui.components.WordAction
import com.speakmind.app.ui.theme.TtsSpeedManager
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class ArticleDetailUiState(
    val scenario: Scenario? = null,
    val isLoading: Boolean = true,
    val isSpeaking: Boolean = false,
    val selectedWord: String? = null,
    val wordSaved: Boolean = false,
    val selectedAction: WordAction = WordAction.SAVE,
    val meaningText: String? = null,
    val partOfSpeech: String? = null,
    val translationText: String? = null,
    val isLoadingAction: Boolean = false,
)

class ArticleDetailViewModel(
    private val scenarioId: String,
    private val scenarioRepository: ScenarioRepository,
    private val newsInLevelsRepository: NewsInLevelsRepository,
    private val navigationManager: NavigationManager,
    private val ttsEngine: TextToSpeechEngine,
    private val database: SpeakyDatabase,
    private val aiEngineProvider: AiEngineProvider,
    private val ttsSpeedManager: TtsSpeedManager,
    private val dictionaryClient: DictionaryApiClient,
    private val wiktionaryClient: WiktionaryApiClient,
    private val translationClient: TranslationApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleDetailUiState())
    val uiState: StateFlow<ArticleDetailUiState> = _uiState.asStateFlow()

    private var speakJob: Job? = null

    init {
        loadArticle()
        viewModelScope.launch {
            ttsEngine.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }
        viewModelScope.launch {
            ttsSpeedManager.speed.drop(1).collect {
                if (_uiState.value.isSpeaking) startListening()
            }
        }
    }

    private fun loadArticle() {
        viewModelScope.launch {
            val scenario = scenarioRepository.getScenarioById(scenarioId)
                ?: newsInLevelsRepository.getTopicById(scenarioId)
            _uiState.value = ArticleDetailUiState(
                scenario = scenario,
                isLoading = false,
            )
        }
    }

    fun onBackClicked() {
        speakJob?.cancel()
        ttsEngine.stop()
        navigationManager.back()
    }

    fun onListenClicked() {
        if (_uiState.value.isSpeaking) {
            speakJob?.cancel()
            ttsEngine.stop()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        val scenario = _uiState.value.scenario ?: return
        speakJob?.cancel()
        ttsEngine.stop()
        speakJob = viewModelScope.launch {
            val baseRate = when (scenario.level) {
                "A1" -> 0.7f
                "A2" -> 0.8f
                "B1" -> 0.9f
                else -> 1.0f
            }
            ttsEngine.speak(scenario.emotionalStakes, rate = baseRate * ttsSpeedManager.speed.value)
        }
    }

    fun onDiscussClicked() {
        ttsEngine.stop()
        viewModelScope.launch {
            when {
                aiEngineProvider.hasGeminiKey() -> {
                    database.speakMindQueries.updateAiEngine("gemini_api")
                    navigationManager.navigate(ChatDestination(scenarioId = scenarioId))
                }
                aiEngineProvider.hasLocalModel() -> {
                    database.speakMindQueries.updateAiEngine("local")
                    navigationManager.navigate(ChatDestination(scenarioId = scenarioId))
                }
                else -> navigationManager.navigate(AiSetupDestination(scenarioId = scenarioId))
            }
        }
    }

    fun onWordClicked(word: String) {
        val cleaned = word.replace(Regex("[^a-zA-Z'-]"), "")
        if (cleaned.length < 2) return
        _uiState.value = _uiState.value.copy(
            selectedWord = cleaned,
            wordSaved = false,
            selectedAction = WordAction.SAVE,
            meaningText = null,
            partOfSpeech = null,
            translationText = null,
            isLoadingAction = false,
        )
    }

    fun onActionSelected(action: WordAction) {
        val word = _uiState.value.selectedWord ?: return
        _uiState.value = _uiState.value.copy(selectedAction = action)
        when (action) {
            WordAction.MEANING -> if (_uiState.value.meaningText == null) fetchMeaning(word)
            WordAction.TRANSLATE -> if (_uiState.value.translationText == null) fetchTranslation(word)
            WordAction.SAVE -> Unit
        }
    }

    fun onSpeakWord() {
        val word = _uiState.value.selectedWord ?: return
        viewModelScope.launch { ttsEngine.speak(word, rate = ttsSpeedManager.speed.value) }
    }

    private fun fetchMeaning(word: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAction = true)
            val result = withContext(Dispatchers.IO) {
                dictionaryClient.lookup(word) ?: wiktionaryClient.lookup(word)
            }
            _uiState.value = _uiState.value.copy(
                meaningText = result?.meaning,
                partOfSpeech = result?.partOfSpeech?.takeIf { it.isNotEmpty() },
                isLoadingAction = false,
            )
        }
    }

    private fun fetchTranslation(word: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAction = true)
            val translation = withContext(Dispatchers.IO) {
                translationClient.translateToArabic(word)
            }
            _uiState.value = _uiState.value.copy(
                translationText = translation,
                isLoadingAction = false,
            )
        }
    }

    fun onDismissWord() {
        _uiState.value = _uiState.value.copy(
            selectedWord = null,
            wordSaved = false,
            selectedAction = WordAction.SAVE,
            meaningText = null,
            partOfSpeech = null,
            translationText = null,
        )
    }

    fun onSaveWordToFlashcard() {
        val word = _uiState.value.selectedWord ?: return
        val scenario = _uiState.value.scenario ?: return
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val sentence = findSentenceContaining(scenario.emotionalStakes, word)
            database.speakMindQueries.insertFlashcard(
                word = word,
                sentence = sentence,
                context = "Article: ${scenario.title}",
                grammar_note = "",
                error_type = "",
                next_review = now,
                interval_days = 1.0,
                ease_factor = 2.5,
                repetitions = 0,
                created_at = now,
                source_scenario_id = scenario.id,
            )
            database.speakMindQueries.incrementVocab()
            _uiState.value = _uiState.value.copy(wordSaved = true)
        }
    }

    private fun findSentenceContaining(content: String, word: String): String {
        val sentences = content.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        return sentences.firstOrNull { it.contains(word, ignoreCase = true) }
            ?.let { "$it." }
            ?: content.take(100)
    }
}
