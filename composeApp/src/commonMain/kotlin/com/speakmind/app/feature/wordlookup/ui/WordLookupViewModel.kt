package com.speakmind.app.feature.wordlookup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.ai.domain.AiEngine
import com.speakmind.app.feature.ai.platform.AiEngineProvider
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.ui.theme.TtsSpeedManager
import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import com.speakmind.app.navigation.AiSetupDestination
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface WordLookupUiState {
    data object Idle : WordLookupUiState
    data object Loading : WordLookupUiState
    data class Result(val result: WordLookupResult, val isSaved: Boolean = false) : WordLookupUiState
    data class NotFound(val hasAi: Boolean) : WordLookupUiState
}

class WordLookupViewModel(
    private val dictionaryClient: DictionaryApiClient,
    private val aiEngineProvider: AiEngineProvider,
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
                // Show dictionary result immediately
                val alreadySaved = dailyWordRepository.getWordByWord(dictResult.word) != null
                _uiState.value = WordLookupUiState.Result(dictResult, isSaved = alreadySaved)

                // Then try AI enhancement silently in background
                val engine = aiEngineProvider.getActiveEngine()
                if (engine != null) {
                    try {
                        val enhanced = enhanceWithAi(engine, dictResult)
                        if (enhanced != null) {
                            _uiState.value = WordLookupUiState.Result(enhanced, isSaved = alreadySaved)
                        }
                    } catch (_: Exception) { /* keep dictionary result */ }
                }
            } else {
                // Dictionary found nothing — try AI directly
                val engine = aiEngineProvider.getActiveEngine()
                if (engine != null) {
                    try {
                        val aiResult = lookUpWithAi(engine, word.trim())
                        if (aiResult != null) {
                            val alreadySaved = dailyWordRepository.getWordByWord(aiResult.word) != null
                            _uiState.value = WordLookupUiState.Result(aiResult, isSaved = alreadySaved)
                        } else {
                            _uiState.value = WordLookupUiState.NotFound(hasAi = true)
                        }
                    } catch (_: Exception) {
                        _uiState.value = WordLookupUiState.NotFound(hasAi = true)
                    }
                } else {
                    _uiState.value = WordLookupUiState.NotFound(hasAi = false)
                }
            }
        }
    }

    private suspend fun enhanceWithAi(engine: AiEngine, base: WordLookupResult): WordLookupResult? {
        val prompt = """For the English word "${base.word}", respond with ONLY valid JSON, no markdown:
{"level":"B2","examples":["sentence 1","sentence 2","sentence 3"]}
Use CEFR levels A1-C2. Make examples natural for English learners."""
        val raw = engine.chat(
            messages = listOf(ChatMessage(id = "wl_enhance", role = MessageRole.USER, content = prompt)),
            userLevel = "B1",
        )
        return try {
            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = Json.parseToJsonElement(clean).jsonObject
            val level = json["level"]?.jsonPrimitive?.content
            val examples = json["examples"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.filter { it.isNotBlank() }
                ?: base.examples
            base.copy(
                level = level ?: base.level,
                examples = examples.ifEmpty { base.examples },
                source = WordLookupResult.Source.AI_ENHANCED,
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun lookUpWithAi(engine: AiEngine, word: String): WordLookupResult? {
        val prompt = """Define the English word "$word". Respond with ONLY valid JSON, no markdown:
{"word":"$word","partOfSpeech":"noun","level":"B1","meaning":"clear definition","examples":["sentence 1","sentence 2","sentence 3"]}"""
        val raw = engine.chat(
            messages = listOf(ChatMessage(id = "wl_query", role = MessageRole.USER, content = prompt)),
            userLevel = "B1",
        )
        return try {
            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = Json.parseToJsonElement(clean).jsonObject
            val w = json["word"]?.jsonPrimitive?.content ?: return null
            val pos = json["partOfSpeech"]?.jsonPrimitive?.content ?: ""
            val level = json["level"]?.jsonPrimitive?.content
            val meaning = json["meaning"]?.jsonPrimitive?.content ?: return null
            val examples = json["examples"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            WordLookupResult(
                word = w,
                phonetic = "",
                audioUrl = null,
                partOfSpeech = pos,
                meaning = meaning,
                examples = examples,
                level = level,
                source = WordLookupResult.Source.AI_ONLY,
            )
        } catch (_: Exception) {
            null
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

    fun onSetupAi() {
        navigationManager.navigate(AiSetupDestination())
    }
}
