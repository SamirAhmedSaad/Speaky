package com.speakmind.app.feature.vocabulary.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.vocabulary.data.VocabularyRepository
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.VocabWordListDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VocabLevelSummary(
    val level: String,
    val label: String,
    val description: String,
    val wordCount: Int,
    /** Words of this level marked as learned (saved to review from the word list). */
    val learnedCount: Int = 0,
)

data class VocabCategoryUiState(
    val levels: List<VocabLevelSummary> = emptyList(),
    val isLoading: Boolean = true,
    val userLevel: String? = null,
)

class VocabCategoryViewModel(
    private val repository: VocabularyRepository,
    private val navigationManager: NavigationManager,
    private val database: SpeakyDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabCategoryUiState())
    val uiState: StateFlow<VocabCategoryUiState> = _uiState.asStateFlow()

    init {
        loadLevels()
    }

    private fun loadLevels() {
        viewModelScope.launch {
            val levels = repository.getLevels()
            // Same marker the word list uses to tell which words are learned.
            val learnedByLevel = database.speakMindQueries.selectAllFlashcards().executeAsList()
                .filter { it.context.startsWith("Word Builder:") }
                .groupBy { it.context.removePrefix("Word Builder:").trim() }
                .mapValues { (_, cards) -> cards.map { it.word }.toSet() }
            val userLevel = database.speakMindQueries.selectProgress().executeAsOneOrNull()?.level
            _uiState.value = VocabCategoryUiState(
                levels = levels.map { level ->
                    val learned = learnedByLevel[level.level].orEmpty()
                    VocabLevelSummary(
                        level = level.level,
                        label = level.label,
                        description = level.description,
                        wordCount = level.words.size,
                        learnedCount = level.words.count { it.word in learned },
                    )
                },
                isLoading = false,
                userLevel = userLevel,
            )
        }
    }

    fun onLevelClicked(level: String) {
        navigationManager.navigate(VocabWordListDestination(level = level))
    }

    fun onGoBack() {
        navigationManager.back()
    }
}
