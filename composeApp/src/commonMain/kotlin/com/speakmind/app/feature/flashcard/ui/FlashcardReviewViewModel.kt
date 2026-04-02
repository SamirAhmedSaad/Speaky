package com.speakmind.app.feature.flashcard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.Flashcards
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.flashcard.domain.SM2Engine
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.TtsSpeedManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

enum class FlashcardTab { DUE, ALL_WORDS }

data class FlashcardReviewUiState(
    val cards: List<Flashcards> = emptyList(),
    val allCards: List<Flashcards> = emptyList(),
    val expandedCardId: Long? = null,
    val isComplete: Boolean = false,
    val isLoading: Boolean = true,
    val reviewedCount: Int = 0,
    val lastRatingMessage: String? = null,
    val selectedTab: FlashcardTab = FlashcardTab.DUE,
)

class FlashcardReviewViewModel(
    private val database: SpeakyDatabase,
    private val navigationManager: NavigationManager,
    private val ttsEngine: TextToSpeechEngine,
    private val ttsSpeedManager: TtsSpeedManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashcardReviewUiState())
    val uiState: StateFlow<FlashcardReviewUiState> = _uiState.asStateFlow()

    val isSpeaking = ttsEngine.isSpeaking

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val dueCards = database.speakMindQueries.selectDueFlashcards(now).executeAsList()
            val allCards = database.speakMindQueries.selectAllFlashcards().executeAsList()

            _uiState.value = FlashcardReviewUiState(
                cards = dueCards,
                allCards = allCards,
                isComplete = dueCards.isEmpty(),
                isLoading = false,
            )
        }
    }

    fun onTabSelected(tab: FlashcardTab) {
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            expandedCardId = null,
        )
    }

    fun onCardClicked(cardId: Long) {
        val current = _uiState.value.expandedCardId
        _uiState.value = _uiState.value.copy(
            expandedCardId = if (current == cardId) null else cardId
        )
    }

    fun onSpeak(text: String) {
        viewModelScope.launch {
            ttsEngine.speak(text, rate = ttsSpeedManager.speed.value)
        }
    }

    fun onRate(cardId: Long, rating: String) {
        viewModelScope.launch {
            val card = _uiState.value.cards.firstOrNull { it.id == cardId } ?: return@launch

            val quality = SM2Engine.qualityFromRating(rating)
            val result = SM2Engine.review(
                quality = quality,
                repetitions = card.repetitions,
                easeFactor = card.ease_factor,
                intervalDays = card.interval_days,
            )

            val nextReview = Clock.System.now()
                .plus(result.nextIntervalDays.days)
                .toEpochMilliseconds()

            database.speakMindQueries.updateFlashcardReview(
                next_review = nextReview,
                interval_days = result.nextIntervalDays,
                ease_factor = result.easeFactor,
                repetitions = result.repetitions,
                id = card.id,
            )

            val updatedCards = _uiState.value.cards.filter { it.id != cardId }
            val reviewed = _uiState.value.reviewedCount + 1

            val message = "We'll bring this word back for revision later."

            _uiState.value = _uiState.value.copy(
                cards = updatedCards,
                expandedCardId = null,
                reviewedCount = reviewed,
                isComplete = updatedCards.isEmpty(),
                lastRatingMessage = message,
            )
        }
    }

    fun onDeleteCard(cardId: Long) {
        viewModelScope.launch {
            database.speakMindQueries.deleteFlashcard(cardId)
            val updatedCards = _uiState.value.cards.filter { it.id != cardId }
            val updatedAll = _uiState.value.allCards.filter { it.id != cardId }
            _uiState.value = _uiState.value.copy(
                cards = updatedCards,
                allCards = updatedAll,
                expandedCardId = null,
                isComplete = updatedCards.isEmpty(),
            )
        }
    }

    fun onGoBack() {
        ttsEngine.stop()
        navigationManager.back()
    }
}
