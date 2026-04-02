package com.speakmind.app.feature.wordlookup.di

import com.speakmind.app.feature.wordlookup.data.DictionaryApiClient
import com.speakmind.app.feature.wordlookup.ui.WordLookupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val wordLookupModule = module {
    single { DictionaryApiClient(get()) }
    viewModel { WordLookupViewModel(get(), get(), get(), get(), get(), get()) }
}
