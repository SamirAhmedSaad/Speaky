package com.speakmind.app.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.dailyword.domain.DailyWordService
import com.speakmind.app.feature.dailyword.domain.model.DailyWordData
import com.speakmind.app.feature.dailyword.platform.DailyWordNotificationScheduler
import com.speakmind.app.feature.home.domain.DailyTopicService
import com.speakmind.app.feature.home.domain.model.DailyCard
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.geminichat.data.ApiKeyStore
import com.speakmind.app.ui.theme.ThemeManager
import com.speakmind.app.navigation.AiSetupDestination
import com.speakmind.app.navigation.ArticleDetailDestination
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.navigation.FlashcardReviewDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.CommunitySetupDestination
import com.speakmind.app.navigation.PrivacyPolicyDestination
import com.speakmind.app.navigation.StoriesDestination
import com.speakmind.app.navigation.VocabCategoryDestination
import com.speakmind.app.navigation.WordDetailDestination
import com.speakmind.app.navigation.WordLookupDestination
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
    val userName: String = "",
    val totalVocab: Long = 0,
    val totalConversations: Long = 0,
    val totalMinutes: Long = 0,
    val isLoading: Boolean = true,
    val isLoadingTopics: Boolean = true,
    val showLevelPicker: Boolean = false,
    val todayWord: DailyWordData? = null,
    val recentDailyWords: List<DailyWordData> = emptyList(),
    val notificationHour: Int = 22,
    val notificationMinute: Int = 0,
    val notificationsEnabled: Boolean = true,
    val showTimePicker: Boolean = false,
    val showExactAlarmRationale: Boolean = false,
    val communityUnreadCount: Int = 0,
)

val ALL_LEVELS = listOf("A1", "A2", "B1", "B2", "C1")

class HomeViewModel(
    private val navigationManager: NavigationManager,
    private val dailyTopicService: DailyTopicService,
    private val database: SpeakyDatabase,
    private val modelDownloader: ModelDownloader,
    private val apiKeyStore: ApiKeyStore,
    private val themeManager: ThemeManager,
    private val dailyWordService: DailyWordService,
    private val dailyWordRepository: DailyWordRepository,
    private val notificationScheduler: DailyWordNotificationScheduler,
    private val communityRepository: CommunityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
        observeCommunityUnread()
    }

    private fun observeCommunityUnread() {
        // Polls SQLite every 3s — picks up markChatRead resets immediately
        viewModelScope.launch {
            try {
                communityRepository.getTotalUnreadCount().collect { count ->
                    _uiState.value = _uiState.value.copy(communityUnreadCount = count)
                }
            } catch (_: Exception) {}
        }
        // Firestore real-time listener — increments SQLite on new incoming messages
        viewModelScope.launch {
            try {
                communityRepository.observeAllChatsForUnread().collect { count ->
                    _uiState.value = _uiState.value.copy(communityUnreadCount = count)
                }
            } catch (_: Exception) {}
        }
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

            // Update streak
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            val lastActive = progress?.last_active_date ?: ""
            val streak = progress?.streak_days ?: 0

            // Show UI immediately with loading topics indicator
            _uiState.value = HomeUiState(
                streakDays = streak.toInt(),
                flashcardDueCount = dueCount,
                userLevel = userLevel,
                userName = progress?.user_name ?: "",
                totalVocab = totalVocab,
                totalConversations = progress?.total_conversations ?: 0,
                totalMinutes = progress?.total_minutes ?: 0,
                isLoading = false,
                isLoadingTopics = true,
            )

            // Load daily word + settings
            database.speakMindQueries.insertDefaultSettings()
            val settings = database.speakMindQueries.selectSettings().executeAsOneOrNull()
            val todayWord = dailyWordService.getOrCreateTodayWord(userLevel)
            val recentWords = dailyWordRepository.getRecentWords()

            val notifEnabled = settings?.notifications_enabled != 0L
            val notifHour = settings?.notification_hour?.toInt() ?: 22
            val notifMinute = settings?.notification_minute?.toInt() ?: 0

            // Increment launch count before reading it (first launch becomes 1)
            database.speakMindQueries.incrementLaunchCount()
            val updatedSettings = database.speakMindQueries.selectSettings().executeAsOneOrNull()
            val launchCount = updatedSettings?.app_launch_count ?: 1L
            val dialogCount = updatedSettings?.exact_alarm_dialog_count ?: 0L

            _uiState.value = _uiState.value.copy(
                todayWord = todayWord,
                recentDailyWords = recentWords,
                notificationHour = notifHour,
                notificationMinute = notifMinute,
                notificationsEnabled = notifEnabled,
            )

            // Re-arm the alarm on every app launch (survives app updates)
            // Show rationale only on launch 2+ and only up to 2 times total
            if (notifEnabled) {
                val wordForNotif = todayWord ?: recentWords.firstOrNull()
                if (wordForNotif != null) {
                    val needsPermission = notificationScheduler.schedule(notifHour, notifMinute, wordForNotif.word, wordForNotif.meaning)
                    if (needsPermission && launchCount > 1L && dialogCount < 2L) {
                        database.speakMindQueries.incrementExactAlarmDialogCount()
                        _uiState.value = _uiState.value.copy(showExactAlarmRationale = true)
                    }
                }
            }

            // Prepare next day's word for notification content
            dailyWordService.prepareNextDayWord(userLevel)

            // Fetch daily topics (may involve network)
            val scenarios = dailyTopicService.getDailyScenarios(userLevel)
            _uiState.value = _uiState.value.copy(
                dailyCards = scenarios.map { DailyCard(it) },
                isLoadingTopics = false,
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

    fun refreshFlashcardCount() {
        viewModelScope.launch {
            val dueCount = database.speakMindQueries
                .countDueFlashcards(Clock.System.now().toEpochMilliseconds())
                .executeAsOne()
            val totalVocab = database.speakMindQueries.countAllFlashcards().executeAsOne()
            _uiState.value = _uiState.value.copy(
                flashcardDueCount = dueCount,
                totalVocab = totalVocab,
            )
        }
    }

    fun onScenarioClicked(scenarioId: String) {
        // Open article detail screen — user can read first, then discuss with Sage
        navigationManager.navigate(ArticleDetailDestination(scenarioId = scenarioId))
    }

    fun onFreeTalkClicked() {
        viewModelScope.launch {
            when {
                // Gemini takes priority whenever a key is set
                apiKeyStore.hasKey() -> {
                    database.speakMindQueries.updateAiEngine("gemini_api")
                    navigationManager.navigate(ChatDestination(scenarioId = null))
                }
                modelDownloader.modelExists() -> {
                    database.speakMindQueries.updateAiEngine("local")
                    navigationManager.navigate(ChatDestination(scenarioId = null))
                }
                else -> navigationManager.navigate(AiSetupDestination(scenarioId = null))
            }
        }
    }

    fun onCloudChatClicked() = onFreeTalkClicked()

    fun onFlashcardsClicked() {
        navigationManager.navigate(FlashcardReviewDestination(showAllWords = true))
    }

    fun onAllLearnedWordsClicked() {
        navigationManager.navigate(FlashcardReviewDestination(showAllWords = true))
    }

    fun onVocabularyClicked() {
        navigationManager.navigate(VocabCategoryDestination)
    }

    fun onStoriesClicked() {
        navigationManager.navigate(StoriesDestination)
    }

    fun onWordLookupClicked() {
        navigationManager.navigate(WordLookupDestination)
    }

    fun onPrivacyPolicyClicked() {
        navigationManager.navigate(PrivacyPolicyDestination)
    }

    fun onCommunityClicked() {
        navigationManager.navigate(CommunitySetupDestination)
    }

    fun onDailyWordClicked(id: Long) {
        navigationManager.navigate(WordDetailDestination(wordId = id))
    }

    fun onNotificationTimeChanged(hour: Int, minute: Int) {
        viewModelScope.launch {
            database.speakMindQueries.updateNotificationTime(hour.toLong(), minute.toLong())
            _uiState.value = _uiState.value.copy(
                notificationHour = hour,
                notificationMinute = minute,
                showTimePicker = false,
            )
            // Reschedule notification
            val state = _uiState.value
            val nextWord = state.todayWord ?: state.recentDailyWords.firstOrNull()
            if (state.notificationsEnabled && nextWord != null) {
                val needsPermission = notificationScheduler.schedule(hour, minute, nextWord.word, nextWord.meaning)
                if (needsPermission) {
                    _uiState.value = _uiState.value.copy(showExactAlarmRationale = true)
                }
            }
        }
    }

    fun onNotificationToggled(enabled: Boolean) {
        viewModelScope.launch {
            database.speakMindQueries.updateNotificationsEnabled(if (enabled) 1 else 0)
            _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
            if (enabled) {
                val state = _uiState.value
                val nextWord = state.todayWord ?: state.recentDailyWords.firstOrNull()
                if (nextWord != null) {
                    val needsPermission = notificationScheduler.schedule(
                        state.notificationHour,
                        state.notificationMinute,
                        nextWord.word,
                        nextWord.meaning,
                    )
                    if (needsPermission) {
                        _uiState.value = _uiState.value.copy(showExactAlarmRationale = true)
                    }
                }
            } else {
                notificationScheduler.cancel()
            }
        }
    }

    fun onExactAlarmRationaleDismissed() {
        _uiState.value = _uiState.value.copy(showExactAlarmRationale = false)
    }

    fun onResumeFromExactAlarmSettings() {
        _uiState.value = _uiState.value.copy(showExactAlarmRationale = false)
        val state = _uiState.value
        if (!state.notificationsEnabled) return
        val word = state.todayWord ?: state.recentDailyWords.firstOrNull() ?: return
        notificationScheduler.schedule(state.notificationHour, state.notificationMinute, word.word, word.meaning)
    }

    fun onTimePickerClicked() {
        _uiState.value = _uiState.value.copy(showTimePicker = true)
    }

    fun onTimePickerDismissed() {
        _uiState.value = _uiState.value.copy(showTimePicker = false)
    }

    fun onLevelBadgeClicked() {
        _uiState.value = _uiState.value.copy(showLevelPicker = true)
    }

    fun onLevelPickerDismissed() {
        _uiState.value = _uiState.value.copy(showLevelPicker = false)
    }

    fun onThemeToggle(currentlyDark: Boolean) {
        themeManager.toggle(currentlyDark)
    }

    fun onLevelSelected(level: String) {
        viewModelScope.launch {
            database.speakMindQueries.updateLevel(level)
            _uiState.value = _uiState.value.copy(
                userLevel = level,
                showLevelPicker = false,
                dailyCards = emptyList(),
                isLoadingTopics = true,
            )
            // Reload scenarios for the new level
            val scenarios = dailyTopicService.getDailyScenarios(level)
            _uiState.value = _uiState.value.copy(
                dailyCards = scenarios.map { DailyCard(it) },
                isLoadingTopics = false,
            )
        }
    }
}
