package com.speakmind.app.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakMindDatabase
import com.speakmind.app.feature.home.data.ScenarioRepository
import com.speakmind.app.feature.home.domain.model.DailyCard
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.navigation.FlashcardReviewDestination
import com.speakmind.app.navigation.ModelDownloadDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class HomeUiState(
    val dailyCards: List<DailyCard> = emptyList(),
    val streakDays: Int = 0,
    val flashcardDueCount: Long = 0,
    val userLevel: String = "A2",
    val totalVocab: Long = 0,
    val totalConversations: Long = 0,
    val totalMinutes: Long = 0,
    val isLoading: Boolean = true,
    val showLevelPicker: Boolean = false,
)

val ALL_LEVELS = listOf("A1", "A2", "B1", "B2", "C1")

class HomeViewModel(
    private val navigationManager: NavigationManager,
    private val scenarioRepository: ScenarioRepository,
    private val database: SpeakMindDatabase,
    private val modelDownloader: ModelDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            // Ensure progress row exists
            database.speakMindQueries.insertDefaultProgress()

            val progress = database.speakMindQueries.selectProgress().executeAsOneOrNull()
            val dueCount = database.speakMindQueries.countDueFlashcards(
                Clock.System.now().toEpochMilliseconds()
            ).executeAsOne()
            val totalVocab = database.speakMindQueries.countAllFlashcards().executeAsOne()

            val userLevel = progress?.level ?: "A2"
            val scenarios = scenarioRepository.getDailyScenarios(userLevel)

            // Update streak
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            val lastActive = progress?.last_active_date ?: ""
            val streak = progress?.streak_days ?: 0

            _uiState.value = HomeUiState(
                dailyCards = scenarios.map { DailyCard(it) },
                streakDays = streak.toInt(),
                flashcardDueCount = dueCount,
                userLevel = userLevel,
                totalVocab = totalVocab,
                totalConversations = progress?.total_conversations ?: 0,
                totalMinutes = progress?.total_minutes ?: 0,
                isLoading = false,
            )

            // Update last active date
            if (lastActive != today) {
                val newStreak = if (isConsecutiveDay(lastActive, today)) streak + 1 else 1
                database.speakMindQueries.updateStreak(newStreak, today)
                _uiState.value = _uiState.value.copy(streakDays = newStreak.toInt())
            }
        }
    }

    private fun isConsecutiveDay(last: String, today: String): Boolean {
        if (last.isEmpty()) return false
        return try {
            val lastDate = kotlinx.datetime.LocalDate.parse(last)
            val todayDate = kotlinx.datetime.LocalDate.parse(today)
            val diff = todayDate.toEpochDays() - lastDate.toEpochDays()
            diff == 1
        } catch (_: Exception) {
            false
        }
    }

    fun onScenarioClicked(scenarioId: String) {
        if (modelDownloader.modelExists()) {
            navigationManager.navigate(ChatDestination(scenarioId = scenarioId))
        } else {
            navigationManager.navigate(ModelDownloadDestination(scenarioId = scenarioId))
        }
    }

    fun onFreeTalkClicked() {
        if (modelDownloader.modelExists()) {
            navigationManager.navigate(ChatDestination(scenarioId = null))
        } else {
            navigationManager.navigate(ModelDownloadDestination(scenarioId = null))
        }
    }

    fun onFlashcardsClicked() {
        navigationManager.navigate(FlashcardReviewDestination)
    }

    fun onLevelBadgeClicked() {
        _uiState.value = _uiState.value.copy(showLevelPicker = true)
    }

    fun onLevelPickerDismissed() {
        _uiState.value = _uiState.value.copy(showLevelPicker = false)
    }

    fun onLevelSelected(level: String) {
        viewModelScope.launch {
            database.speakMindQueries.updateLevel(level)
            _uiState.value = _uiState.value.copy(
                userLevel = level,
                showLevelPicker = false,
                isLoading = true,
            )
            // Reload scenarios for the new level
            val scenarios = scenarioRepository.getDailyScenarios(level)
            _uiState.value = _uiState.value.copy(
                dailyCards = scenarios.map { DailyCard(it) },
                isLoading = false,
            )
        }
    }
}
