package com.speakmind.app.feature.flashcard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.Flashcards
import com.speakmind.app.db.SpeakMindDatabase
import com.speakmind.app.feature.flashcard.domain.SM2Engine
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

data class FlashcardReviewUiState(
    val currentCard: Flashcards? = null,
    val isFlipped: Boolean = false,
    val remainingCount: Int = 0,
    val reviewedCount: Int = 0,
    val isComplete: Boolean = false,
    val isLoading: Boolean = true,
)

class FlashcardReviewViewModel(
    private val database: SpeakMindDatabase,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashcardReviewUiState())
    val uiState: StateFlow<FlashcardReviewUiState> = _uiState.asStateFlow()

    private var dueCards = mutableListOf<Flashcards>()

    init {
        loadDueCards()
    }

    private fun loadDueCards() {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val cards = database.speakMindQueries.selectDueFlashcards(now).executeAsList()
            dueCards = cards.toMutableList()

            if (dueCards.isEmpty()) {
                _uiState.value = FlashcardReviewUiState(
                    isComplete = true,
                    isLoading = false,
                )
            } else {
                _uiState.value = FlashcardReviewUiState(
                    currentCard = dueCards.firstOrNull(),
                    remainingCount = dueCards.size,
                    isLoading = false,
                )
            }
        }
    }

    fun onFlip() {
        _uiState.value = _uiState.value.copy(isFlipped = !_uiState.value.isFlipped)
    }

    fun onRate(rating: String) {
        val card = _uiState.value.currentCard ?: return

        viewModelScope.launch {
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

            dueCards.removeFirstOrNull()
            val reviewed = _uiState.value.reviewedCount + 1

            if (dueCards.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isComplete = true,
                    reviewedCount = reviewed,
                    currentCard = null,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    currentCard = dueCards.first(),
                    isFlipped = false,
                    remainingCount = dueCards.size,
                    reviewedCount = reviewed,
                )
            }
        }
    }

    fun onGoBack() {
        navigationManager.back()
    }
}
