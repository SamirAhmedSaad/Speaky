package com.speakmind.app.feature.wordlookup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.feature.wordlookup.data.WiktionaryApiClient
import com.speakmind.app.ui.theme.TtsSpeedManager
import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface WordLookupUiState {
    data object Idle : WordLookupUiState
    data object Loading : WordLookupUiState
    data class Result(val result: WordLookupResult, val isSaved: Boolean = false) : WordLookupUiState
    data object NotFound : WordLookupUiState
}

class WordLookupViewModel(
    private val dictionaryClient: DictionaryApiClient,
    private val wiktionaryClient: WiktionaryApiClient,
    private val tts: TextToSpeechEngine,
    private val navigationManager: NavigationManager,
    private val ttsSpeedManager: TtsSpeedManager,
    private val dailyWordRepository: DailyWordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WordLookupUiState>(WordLookupUiState.Idle)
    val uiState: StateFlow<WordLookupUiState> = _uiState.asStateFlow()

    fun lookUp(word: String) {
        if (word.isBlank()) return
        viewModelScope.launch {
            _uiState.value = WordLookupUiState.Loading

            val dictResult = dictionaryClient.lookup(word.trim())

            if (dictResult != null) {
                // Supplement with Wiktionary examples if dictionary has none
                val result = if (dictResult.examples.isEmpty()) {
                    val wikiExamples = wiktionaryClient.lookupExamples(word.trim())
                    if (wikiExamples.isNotEmpty()) {
                        dictResult.copy(examples = wikiExamples, source = WordLookupResult.Source.WIKTIONARY)
                    } else {
                        dictResult
                    }
                } else {
                    dictResult
                }
                val alreadySaved = dailyWordRepository.getWordByWord(result.word) != null
                _uiState.value = WordLookupUiState.Result(result, isSaved = alreadySaved)
            } else {
                // Wiktionary as full fallback
                val wikiResult = wiktionaryClient.lookup(word.trim())
                if (wikiResult != null) {
                    val alreadySaved = dailyWordRepository.getWordByWord(wikiResult.word) != null
                    _uiState.value = WordLookupUiState.Result(wikiResult, isSaved = alreadySaved)
                } else {
                    _uiState.value = WordLookupUiState.NotFound
                }
            }
        }
    }

    fun saveWord() {
        val state = _uiState.value as? WordLookupUiState.Result ?: return
        if (state.isSaved) return
        val result = state.result
        viewModelScope.launch {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            dailyWordRepository.insertDailyWord(
                word = result.word,
                level = result.level ?: "",
                partOfSpeech = result.partOfSpeech,
                meaning = result.meaning,
                sentencesJson = Json.encodeToString(result.examples),
                sentDate = today,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
            _uiState.value = state.copy(isSaved = true)
        }
    }

    fun speakWord(word: String) {
        viewModelScope.launch { tts.speak(word, rate = ttsSpeedManager.speed.value) }
    }

    fun speakSentence(sentence: String) {
        viewModelScope.launch { tts.speak(sentence, rate = 0.85f * ttsSpeedManager.speed.value) }
    }

    fun onBack() {
        tts.stop()
        navigationManager.back()
    }
}
