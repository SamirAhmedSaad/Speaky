package com.speakmind.app.feature.vocabgroup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.vocabgroup.data.VocabGroupRepository
import com.speakmind.app.feature.vocabgroup.domain.model.VocabGroupWord
import com.speakmind.app.feature.voice.platform.SpeechRecognizerEngine
import com.speakmind.app.feature.voice.platform.SpeechResult
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.feature.wordlookup.data.TranslationApiClient
import com.speakmind.app.feature.wordlookup.data.WiktionaryApiClient
import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.TtsSpeedManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayAllState(
    val currentIndex: Int,
    val isPaused: Boolean,
    val total: Int,
)

data class GroupDetailUiState(
    val groupName: String = "",
    val words: List<VocabGroupWord> = emptyList(),
    val expandedWordId: Long? = null,
    val loadingWordIds: Set<Long> = emptySet(),
    val showAddSheet: Boolean = false,
    val editingWord: VocabGroupWord? = null,
    val isListening: Boolean = false,
    val playAllState: PlayAllState? = null,
    val lookupError: String? = null,
)

class GroupDetailViewModel(
    private val groupId: Long,
    private val groupName: String,
    private val repository: VocabGroupRepository,
    private val dictionaryClient: DictionaryApiClient,
    private val wiktionaryClient: WiktionaryApiClient,
    private val translationClient: TranslationApiClient,
    private val speechRecognizer: SpeechRecognizerEngine,
    private val tts: TextToSpeechEngine,
    private val ttsSpeedManager: TtsSpeedManager,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupDetailUiState(groupName = groupName))
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private var playJob: Job? = null

    init {
        loadWords()
        observeSpeech()
    }

    private fun loadWords() {
        viewModelScope.launch {
            val words = repository.getWordsInGroup(groupId)
            _uiState.value = _uiState.value.copy(words = words)
        }
    }

    private fun observeSpeech() {
        viewModelScope.launch {
            speechRecognizer.results.collect { result ->
                when (result) {
                    is SpeechResult.Partial -> _inputText.value = result.text
                    is SpeechResult.Final -> {
                        _inputText.value = result.text
                        _uiState.value = _uiState.value.copy(isListening = false)
                    }
                    is SpeechResult.Error -> {
                        _uiState.value = _uiState.value.copy(isListening = false)
                    }
                    else -> {}
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun onShowAddSheet() {
        _uiState.value = _uiState.value.copy(showAddSheet = true)
    }

    fun onDismissAddSheet() {
        if (_uiState.value.isListening) speechRecognizer.stopListening()
        _inputText.value = ""
        _uiState.value = _uiState.value.copy(showAddSheet = false, isListening = false)
    }

    fun onAddWord() {
        val word = _inputText.value.trim()
        if (word.isBlank()) return
        viewModelScope.launch {
            repository.addWord(groupId, word)
            _inputText.value = ""
            _uiState.value = _uiState.value.copy(showAddSheet = false, isListening = false)
            loadWords()
        }
    }

    fun onToggleMic() {
        if (_uiState.value.isListening) {
            speechRecognizer.stopListening()
            _uiState.value = _uiState.value.copy(isListening = false)
        } else {
            _inputText.value = ""
            speechRecognizer.startListening()
            _uiState.value = _uiState.value.copy(isListening = true)
        }
    }

    fun onLookup(word: VocabGroupWord) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingWordIds = _uiState.value.loadingWordIds + word.id,
                expandedWordId = word.id,
            )
            val input = word.word.trim()
            val isPhrase = input.contains(' ') || input.contains('-')

            // Phrases skip Dictionary API (it only handles single words) and go to Wiktionary first.
            // Statements (long phrases) skip directly to translation.
            val result = if (isPhrase) lookupPhrase(input) else tryLookup(input)

            if (result != null) {
                repository.updateWord(
                    id = word.id,
                    word = word.word,
                    meaning = result.meaning,
                    examples = result.examples,
                    phonetic = result.phonetic,
                    partOfSpeech = result.partOfSpeech,
                )
                loadWords()
            } else {
                val arabic = translationClient.translate(input)
                    ?: if (isPhrase) translationClient.translate(input.replace('-', ' ')) else null
                    ?: if (isPhrase) translationClient.translate(input.replace(' ', '-')) else null
                if (arabic != null) {
                    repository.updateWord(
                        id = word.id,
                        word = word.word,
                        meaning = arabic,
                        examples = emptyList(),
                        phonetic = "",
                        partOfSpeech = if (isPhrase) "phrase" else "",
                    )
                    loadWords()
                } else {
                    _uiState.value = _uiState.value.copy(
                        lookupError = "No result found for \"${word.word}\". Check the spelling.",
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                loadingWordIds = _uiState.value.loadingWordIds - word.id,
            )
        }
    }

    private suspend fun lookupPhrase(input: String): WordLookupResult? {
        // Try Wiktionary for idioms/phrasal verbs (e.g. "give up", "break a leg", "rip-off")
        val hyphenated = input.replace(' ', '-')
        val spaced = input.replace('-', ' ')
        val wikiResult = wiktionaryClient.lookup(input)
            ?: wiktionaryClient.lookup(hyphenated)
            ?: wiktionaryClient.lookup(spaced)
            ?: dictionaryClient.lookup(hyphenated) // dictionary API also indexes hyphenated compounds
            ?: dictionaryClient.lookup(spaced)
        return when {
            wikiResult == null -> null
            wikiResult.examples.isEmpty() -> {
                val ex = wiktionaryClient.lookupExamples(input)
                if (ex.isNotEmpty()) wikiResult.copy(examples = ex) else wikiResult
            }
            else -> wikiResult
        }
    }

    fun onToggleExpand(wordId: Long) {
        val current = _uiState.value.expandedWordId
        _uiState.value = _uiState.value.copy(
            expandedWordId = if (current == wordId) null else wordId,
        )
    }

    fun onEditWord(word: VocabGroupWord) {
        _uiState.value = _uiState.value.copy(editingWord = word)
    }

    fun onDismissEdit() {
        _uiState.value = _uiState.value.copy(editingWord = null)
    }

    fun onConfirmEdit(newWord: String) {
        val editing = _uiState.value.editingWord ?: return
        if (newWord.isBlank()) return
        viewModelScope.launch {
            repository.updateWord(
                id = editing.id,
                word = newWord.trim(),
                meaning = editing.meaning,
                examples = editing.examples,
                phonetic = editing.phonetic,
                partOfSpeech = editing.partOfSpeech,
            )
            _uiState.value = _uiState.value.copy(editingWord = null)
            loadWords()
        }
    }

    fun onDeleteWord(wordId: Long) {
        viewModelScope.launch {
            repository.deleteWord(wordId)
            if (_uiState.value.expandedWordId == wordId) {
                _uiState.value = _uiState.value.copy(expandedWordId = null)
            }
            loadWords()
        }
    }

    fun clearLookupError() {
        _uiState.value = _uiState.value.copy(lookupError = null)
    }

    private suspend fun tryLookup(input: String) = run {
        val dictResult = dictionaryClient.lookup(input)
        when {
            dictResult != null && dictResult.examples.isEmpty() -> {
                val wikiExamples = wiktionaryClient.lookupExamples(input)
                if (wikiExamples.isNotEmpty()) dictResult.copy(examples = wikiExamples) else dictResult
            }
            dictResult != null -> dictResult
            else -> wiktionaryClient.lookup(input)
        }
    }

    fun startPlayAll() {
        val words = _uiState.value.words
        if (words.isEmpty()) return
        playFromIndex(0, words)
    }

    fun togglePausePlayAll() {
        val state = _uiState.value.playAllState ?: return
        if (state.isPaused) {
            playFromIndex(state.currentIndex, _uiState.value.words)
        } else {
            playJob?.cancel()
            playJob = null
            tts.stop()
            _uiState.value = _uiState.value.copy(playAllState = state.copy(isPaused = true))
        }
    }

    fun stopPlayAll() {
        playJob?.cancel()
        playJob = null
        tts.stop()
        _uiState.value = _uiState.value.copy(playAllState = null, expandedWordId = null)
    }

    private fun playFromIndex(startIndex: Int, words: List<VocabGroupWord>) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            for (index in startIndex until words.size) {
                val word = words[index]
                _uiState.value = _uiState.value.copy(
                    playAllState = PlayAllState(currentIndex = index, isPaused = false, total = words.size),
                    expandedWordId = word.id,
                )
                tts.speak(word.word, rate = ttsSpeedManager.speed.value)
                delay(2000)
                val example = word.examples.firstOrNull()
                if (example != null) {
                    tts.speak(example, rate = 0.85f * ttsSpeedManager.speed.value)
                    delay(1500)
                } else {
                    delay(500)
                }
            }
            _uiState.value = _uiState.value.copy(playAllState = null, expandedWordId = null)
            playJob = null
        }
    }

    fun speakWord(text: String) {
        viewModelScope.launch { tts.speak(text, rate = ttsSpeedManager.speed.value) }
    }

    fun speakExample(sentence: String) {
        viewModelScope.launch { tts.speak(sentence, rate = 0.85f * ttsSpeedManager.speed.value) }
    }

    fun onBack() {
        stopPlayAll()
        navigationManager.back()
    }
}
