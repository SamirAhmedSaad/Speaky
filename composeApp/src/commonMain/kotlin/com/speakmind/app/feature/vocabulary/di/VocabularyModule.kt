package com.speakmind.app.feature.vocabulary.di

import com.speakmind.app.feature.vocabulary.data.VocabularyRepository
import com.speakmind.app.feature.vocabulary.ui.VocabCategoryViewModel
import com.speakmind.app.feature.vocabulary.ui.VocabWordListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val vocabularyModule = module {
    single { VocabularyRepository() }
    viewModel { VocabCategoryViewModel(get(), get()) }
    viewModel { params ->
        VocabWordListViewModel(params.get(), get(), get(), get(), get(), get())
    }
}
