package com.speakmind.app.feature.story.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.story.data.StoryRepository
import com.speakmind.app.feature.story.domain.model.Story
import com.speakmind.app.feature.story.domain.model.StoryTopic
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.StoryDetailDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoriesUiState(
    val stories: List<Story> = emptyList(),
    val selectedTopic: StoryTopic = StoryTopic.HORROR,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showTopicPicker: Boolean = false,
)

class StoryViewModel(
    private val storyRepository: StoryRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoriesUiState())
    val uiState: StateFlow<StoriesUiState> = _uiState.asStateFlow()

    init {
        loadStories()
    }

    fun onTopicBadgeClicked() {
        _uiState.value = _uiState.value.copy(showTopicPicker = true)
    }

    fun onTopicPickerDismissed() {
        _uiState.value = _uiState.value.copy(showTopicPicker = false)
    }

    fun onTopicSelected(topic: StoryTopic) {
        _uiState.value = _uiState.value.copy(
            selectedTopic = topic,
            showTopicPicker = false,
        )
        loadStories()
    }

    fun onStoryClicked(story: Story) {
        navigationManager.navigate(StoryDetailDestination(storyId = story.id))
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val stories = storyRepository.getStories(
                    topic = _uiState.value.selectedTopic,
                    forceRefresh = true,
                )
                _uiState.value = _uiState.value.copy(
                    stories = stories,
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    fun onBackClicked() {
        navigationManager.back()
    }

    private fun loadStories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val stories = storyRepository.getStories(topic = _uiState.value.selectedTopic)
                _uiState.value = _uiState.value.copy(
                    stories = stories,
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not load stories. Check your internet connection.",
                )
            }
        }
    }
}
