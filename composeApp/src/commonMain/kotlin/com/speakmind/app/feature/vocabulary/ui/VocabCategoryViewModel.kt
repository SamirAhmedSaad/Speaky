package com.speakmind.app.feature.vocabulary.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

data class VocabCategoryUiState(
    val levels: List<VocabLevelSummary> = emptyList(),
    val isLoading: Boolean = true,
)

class VocabCategoryViewModel(
    private val repository: VocabularyRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabCategoryUiState())
    val uiState: StateFlow<VocabCategoryUiState> = _uiState.asStateFlow()

    init {
        loadLevels()
    }

    private fun loadLevels() {
        viewModelScope.launch {
            val levels = repository.getLevels()
            _uiState.value = VocabCategoryUiState(
                levels = levels.map { level ->
                    VocabLevelSummary(
                        level = level.level,
                        label = level.label,
                        description = level.description,
                        wordCount = level.words.size,
                    )
                },
                isLoading = false,
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
