package com.speakmind.app.feature.dailyword.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.dailyword.domain.model.DailyWordData
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.TtsSpeedManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WordDetailUiState(
    val word: DailyWordData? = null,
    val isLoading: Boolean = true,
)

class WordDetailViewModel(
    private val wordId: Long,
    private val wordString: String,
    private val navigationManager: NavigationManager,
    private val repository: DailyWordRepository,
    private val tts: TextToSpeechEngine,
    private val ttsSpeedManager: TtsSpeedManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordDetailUiState())
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    init {
        loadWord()
    }

    private fun loadWord() {
        viewModelScope.launch {
            val word = when {
                wordId > 0 -> repository.getWordById(wordId)
                wordString.isNotEmpty() -> repository.getWordByWord(wordString)
                else -> null
            }
            word?.let { repository.markAsRead(it.id) }
            _uiState.value = WordDetailUiState(word = word, isLoading = false)
        }
    }

    fun speakWord() {
        val word = _uiState.value.word ?: return
        viewModelScope.launch { tts.speak(word.word, rate = ttsSpeedManager.speed.value) }
    }

    fun speakSentence(sentence: String) {
        viewModelScope.launch { tts.speak(sentence, rate = 0.85f * ttsSpeedManager.speed.value) }
    }

    fun onBack() {
        tts.stop()
        navigationManager.back()
    }
}
