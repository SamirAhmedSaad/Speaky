package com.speakmind.app.feature.wordlookup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.feature.wordlookup.data.DatamuseApiClient
import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.feature.wordlookup.data.TranslationApiClient
import com.speakmind.app.feature.wordlookup.data.WiktionaryApiClient
import com.speakmind.app.ui.theme.TtsSpeedManager
import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val datamuseClient: DatamuseApiClient,
    private val translationClient: TranslationApiClient,
    private val tts: TextToSpeechEngine,
    private val navigationManager: NavigationManager,
    private val ttsSpeedManager: TtsSpeedManager,
    private val dailyWordRepository: DailyWordRepository,
    private val database: com.speakmind.app.db.SpeakyDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WordLookupUiState>(WordLookupUiState.Idle)
    val uiState: StateFlow<WordLookupUiState> = _uiState.asStateFlow()

    fun lookUp(input: String) {
        if (input.isBlank()) return
        val text = input.trim()
        val isPhrase = text.contains(' ')

        viewModelScope.launch {
            _uiState.value = WordLookupUiState.Loading

            if (isPhrase) {
                // Phrase / sentence — translate only, no dictionary
                val arabic = translationClient.translate(text)
                if (arabic != null) {
                    _uiState.value = WordLookupUiState.Result(
                        WordLookupResult(
                            word = text,
                            phonetic = "",
                            audioUrl = null,
                            partOfSpeech = "",
                            meaning = "",
                            examples = emptyList(),
                            level = null,
                            arabicTranslation = arabic,
                            source = WordLookupResult.Source.TRANSLATION,
                        )
                    )
                } else {
                    _uiState.value = WordLookupUiState.NotFound
                }
                return@launch
            }

            // Single word — every source at once, each capped so one slow service can't stall the result.
            val translationDeferred = async { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { translationClient.translate(text) } }
            val dictDeferred = async { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { dictionaryClient.lookup(text) } }
            val senseDeferred = async { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { datamuseClient.lookup(text) } }
            val wikiDeferred = async { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { wiktionaryClient.lookup(text) } }
            val dictResult = dictDeferred.await()
            val sense = senseDeferred.await()
            val wikiResult = wikiDeferred.await()
            val arabic = translationDeferred.await()

            if (dictResult != null) {
                val result = dictResult.copy(
                    // Datamuse lists the most common meaning first; the dictionary's order is arbitrary.
                    partOfSpeech = sense?.partOfSpeech ?: dictResult.partOfSpeech,
                    meaning = sense?.meaning ?: dictResult.meaning,
                    examples = dictResult.examples.ifEmpty { wikiResult?.examples.orEmpty() },
                    source = if (dictResult.examples.isEmpty() && !wikiResult?.examples.isNullOrEmpty()) {
                        WordLookupResult.Source.WIKTIONARY
                    } else {
                        dictResult.source
                    },
                    arabicTranslation = arabic,
                )
                val alreadySaved = dailyWordRepository.getWordByWord(result.word) != null
                _uiState.value = WordLookupUiState.Result(result, isSaved = alreadySaved)
            } else {
                if (wikiResult != null) {
                    val alreadySaved = dailyWordRepository.getWordByWord(wikiResult.word) != null
                    _uiState.value = WordLookupUiState.Result(
                        wikiResult.copy(
                            partOfSpeech = sense?.partOfSpeech ?: wikiResult.partOfSpeech,
                            meaning = sense?.meaning ?: wikiResult.meaning,
                            arabicTranslation = arabic,
                        ),
                        isSaved = alreadySaved,
                    )
                } else if (arabic != null) {
                    // At least we have a translation — show it
                    _uiState.value = WordLookupUiState.Result(
                        WordLookupResult(
                            word = text,
                            phonetic = "",
                            audioUrl = null,
                            partOfSpeech = "",
                            meaning = "",
                            examples = emptyList(),
                            level = null,
                            arabicTranslation = arabic,
                            source = WordLookupResult.Source.TRANSLATION,
                        )
                    )
                } else {
                    _uiState.value = WordLookupUiState.NotFound
                }
            }
        }
    }

    fun saveWord() {
        val state = _uiState.value as? WordLookupUiState.Result ?: return
        if (state.isSaved) return
        // Phrases (translation-only) are not saveable as flashcard words
        if (state.result.source == WordLookupResult.Source.TRANSLATION && state.result.meaning.isBlank()) return
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
            // Also a flashcard, so the word shows under "Saved words" and comes up for review.
            val now = Clock.System.now().toEpochMilliseconds()
            database.speakMindQueries.insertFlashcard(
                word = result.word,
                sentence = result.examples.firstOrNull() ?: result.meaning,
                context = "Look-up",
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

    private companion object {
        const val SOURCE_TIMEOUT_MS = 6_000L
    }
}
