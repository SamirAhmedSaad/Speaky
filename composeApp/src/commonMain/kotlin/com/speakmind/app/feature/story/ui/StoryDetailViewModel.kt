package com.speakmind.app.feature.story.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.story.domain.model.Story
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.TtsSpeedManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class StoryDetailUiState(
    val story: Story? = null,
    val isLoading: Boolean = true,
    val isSpeaking: Boolean = false,
    val selectedWord: String? = null,
    val wordSaved: Boolean = false,
)

class StoryDetailViewModel(
    private val storyId: Long,
    private val navigationManager: NavigationManager,
    private val ttsEngine: TextToSpeechEngine,
    private val database: SpeakyDatabase,
    private val ttsSpeedManager: TtsSpeedManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryDetailUiState())
    val uiState: StateFlow<StoryDetailUiState> = _uiState.asStateFlow()

    init {
        loadStory()
        viewModelScope.launch {
            ttsEngine.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }
    }

    fun onBackClicked() {
        ttsEngine.stop()
        navigationManager.back()
    }

    fun onSpeakClicked() {
        val story = _uiState.value.story ?: return
        if (_uiState.value.isSpeaking) {
            ttsEngine.stop()
        } else {
            viewModelScope.launch {
                ttsEngine.speak(story.content, rate = ttsSpeedManager.speed.value)
            }
        }
    }

    fun onWordClicked(word: String) {
        val cleaned = word.replace(Regex("[^a-zA-Z'-]"), "")
        if (cleaned.length < 3) return
        if (cleaned.lowercase() in STOP_WORDS) return
        _uiState.value = _uiState.value.copy(selectedWord = cleaned, wordSaved = false)
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

    fun onDismissWord() {
        _uiState.value = _uiState.value.copy(selectedWord = null, wordSaved = false)
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
}
