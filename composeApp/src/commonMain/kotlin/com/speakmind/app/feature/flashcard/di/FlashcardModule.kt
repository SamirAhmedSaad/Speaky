package com.speakmind.app.feature.flashcard.di

import com.speakmind.app.feature.flashcard.ui.FlashcardReviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val flashcardModule = module {
    viewModel { FlashcardReviewViewModel(get(), get()) }
}
