package com.speakmind.app.feature.story.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.story.domain.model.Story
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.feature.wordlookup.data.TranslationApiClient
import com.speakmind.app.feature.wordlookup.data.WiktionaryApiClient
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.components.WordAction
import com.speakmind.app.ui.theme.TtsSpeedManager
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

data class StoryDetailUiState(
    val story: Story? = null,
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

class StoryDetailViewModel(
    private val storyId: Long,
    private val navigationManager: NavigationManager,
    private val ttsEngine: TextToSpeechEngine,
    private val database: SpeakyDatabase,
    private val ttsSpeedManager: TtsSpeedManager,
    private val dictionaryClient: DictionaryApiClient,
    private val wiktionaryClient: WiktionaryApiClient,
    private val translationClient: TranslationApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryDetailUiState())
    val uiState: StateFlow<StoryDetailUiState> = _uiState.asStateFlow()

    private var speakJob: Job? = null

    init {
        loadStory()
        viewModelScope.launch {
            ttsEngine.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }
        viewModelScope.launch {
            ttsSpeedManager.speed.drop(1).collect {
                if (_uiState.value.isSpeaking) startSpeaking()
            }
        }
    }

    fun onBackClicked() {
        speakJob?.cancel()
        ttsEngine.stop()
        navigationManager.back()
    }

    fun onSpeakClicked() {
        if (_uiState.value.isSpeaking) {
            speakJob?.cancel()
            ttsEngine.stop()
        } else {
            startSpeaking()
        }
    }

    private fun startSpeaking() {
        val story = _uiState.value.story ?: return
        speakJob?.cancel()
        ttsEngine.stop()
        speakJob = viewModelScope.launch {
            ttsEngine.speak(story.content, rate = ttsSpeedManager.speed.value)
        }
    }

    fun onWordClicked(word: String) {
        val cleaned = word.replace(Regex("[^a-zA-Z'-]"), "")
        if (cleaned.length < 3) return
        if (cleaned.lowercase() in STOP_WORDS) return
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
        val story = _uiState.value.story ?: return
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val sentence = findSentenceContaining(story.content, word)
            database.speakMindQueries.insertFlashcard(
                word = word,
                sentence = sentence,
                context = "Story: ${story.title}",
                grammar_note = "",
                error_type = "",
                next_review = now,
                interval_days = 1.0,
                ease_factor = 2.5,
                repetitions = 0,
                created_at = now,
                source_scenario_id = null,
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

    private fun loadStory() {
        viewModelScope.launch {
            val story = database.speakMindQueries
                .selectAllStories()
                .executeAsList()
                .firstOrNull { it.id == storyId }
                ?.let { row ->
                    com.speakmind.app.feature.story.domain.model.Story(
                        id = row.id,
                        title = row.title,
                        content = row.content,
                        link = row.link,
                        level = row.level.toInt(),
                        category = row.category,
                        pubDate = row.pub_date,
                    )
                }
            _uiState.value = StoryDetailUiState(
                story = story,
                isLoading = false,
            )
        }
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "shall", "can", "because",
            "since", "if", "then", "that", "this", "these", "those", "it", "its",
            "he", "she", "they", "we", "you", "i", "me", "him", "her", "them",
            "us", "my", "your", "his", "their", "our", "not", "no", "so", "just",
            "about", "up", "out", "what", "there", "when", "who", "which", "how",
            "all", "any", "also", "into", "than", "more", "some", "such", "like",
            "very", "too", "now", "get", "got", "go", "went", "come", "came",
            "one", "two", "new", "old", "see", "say", "said", "make", "made",
        )
    }
}
