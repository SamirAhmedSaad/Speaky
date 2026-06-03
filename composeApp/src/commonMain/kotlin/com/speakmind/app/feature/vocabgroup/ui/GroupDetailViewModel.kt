package com.speakmind.app.feature.vocabgroup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.vocabgroup.data.VocabGroupRepository
import com.speakmind.app.feature.vocabgroup.domain.model.VocabGroupWord
import com.speakmind.app.feature.voice.platform.SpeechRecognizerEngine
import com.speakmind.app.feature.voice.platform.SpeechResult
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.feature.wordlookup.data.WiktionaryApiClient
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.TtsSpeedManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GroupDetailUiState(
    val groupName: String = "",
    val words: List<VocabGroupWord> = emptyList(),
    val expandedWordId: Long? = null,
    val loadingWordIds: Set<Long> = emptySet(),
    val showAddSheet: Boolean = false,
    val editingWord: VocabGroupWord? = null,
    val isListening: Boolean = false,
)

class GroupDetailViewModel(
    private val groupId: Long,
    private val groupName: String,
    private val repository: VocabGroupRepository,
    private val dictionaryClient: DictionaryApiClient,
    private val wiktionaryClient: WiktionaryApiClient,
    private val speechRecognizer: SpeechRecognizerEngine,
    private val tts: TextToSpeechEngine,
    private val ttsSpeedManager: TtsSpeedManager,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupDetailUiState(groupName = groupName))
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

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
            val dictResult = dictionaryClient.lookup(word.word.trim())
            val result = when {
                dictResult != null && dictResult.examples.isEmpty() -> {
                    val wikiExamples = wiktionaryClient.lookupExamples(word.word.trim())
                    if (wikiExamples.isNotEmpty()) dictResult.copy(examples = wikiExamples) else dictResult
                }
                dictResult != null -> dictResult
                else -> wiktionaryClient.lookup(word.word.trim())
            }
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
            }
            _uiState.value = _uiState.value.copy(
                loadingWordIds = _uiState.value.loadingWordIds - word.id,
            )
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

    fun speakWord(text: String) {
        viewModelScope.launch { tts.speak(text, rate = ttsSpeedManager.speed.value) }
    }

    fun speakExample(sentence: String) {
        viewModelScope.launch { tts.speak(sentence, rate = 0.85f * ttsSpeedManager.speed.value) }
    }

    fun onBack() {
        tts.stop()
        navigationManager.back()
    }
}
