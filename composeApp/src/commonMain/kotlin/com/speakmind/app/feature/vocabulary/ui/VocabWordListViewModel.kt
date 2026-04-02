package com.speakmind.app.feature.vocabulary.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.vocabulary.data.VocabularyRepository
import com.speakmind.app.feature.vocabulary.domain.model.VocabWord
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.TtsSpeedManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class VocabWordListUiState(
    val levelTitle: String = "",
    val levelLabel: String = "",
    val level: String = "",
    val words: List<VocabWord> = emptyList(),
    val expandedWordIndex: Int? = null,
    val isLoading: Boolean = true,
    val learnedWords: Set<String> = emptySet(),
)

class VocabWordListViewModel(
    private val level: String,
    private val repository: VocabularyRepository,
    private val ttsEngine: TextToSpeechEngine,
    private val navigationManager: NavigationManager,
    private val database: SpeakyDatabase,
    private val ttsSpeedManager: TtsSpeedManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabWordListUiState(level = level))
    val uiState: StateFlow<VocabWordListUiState> = _uiState.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = ttsEngine.isSpeaking

    init {
        loadWords()
        loadLearnedWords()
    }

    private fun loadWords() {
        viewModelScope.launch {
            val vocabLevel = repository.getByLevel(level)
            if (vocabLevel != null) {
                _uiState.value = _uiState.value.copy(
                    levelTitle = "${vocabLevel.level} — ${vocabLevel.label}",
                    levelLabel = vocabLevel.label,
                    words = vocabLevel.words,
                    isLoading = false,
                )
            }
        }
    }

    private fun loadLearnedWords() {
        viewModelScope.launch {
            val existing = database.speakMindQueries
                .selectAllFlashcards()
                .executeAsList()
                .filter { it.context != null && it.context.startsWith("Word Builder:") }
                .map { it.word }
                .toSet()
            _uiState.value = _uiState.value.copy(learnedWords = existing)
        }
    }

    fun onWordClicked(index: Int) {
        val current = _uiState.value.expandedWordIndex
        _uiState.value = _uiState.value.copy(
            expandedWordIndex = if (current == index) null else index,
        )
    }

    fun onMarkLearned(word: VocabWord) {
        if (word.word in _uiState.value.learnedWords) return
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            database.speakMindQueries.insertFlashcard(
                word = word.word,
                sentence = word.sentences.firstOrNull() ?: "",
                context = "Word Builder: $level",
                grammar_note = word.meaning,
                error_type = "",
                next_review = now,
                interval_days = 1.0,
                ease_factor = 2.5,
                repetitions = 0,
                created_at = now,
                source_scenario_id = null,
            )
            database.speakMindQueries.incrementVocab()
            _uiState.value = _uiState.value.copy(
                learnedWords = _uiState.value.learnedWords + word.word,
            )
        }
    }

    fun onSpeak(text: String) {
        viewModelScope.launch {
            ttsEngine.speak(text, rate = ttsSpeedManager.speed.value)
        }
    }

    fun onGoBack() {
        navigationManager.back()
    }
}
