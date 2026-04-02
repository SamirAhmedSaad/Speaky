package com.speakmind.app.feature.dailyword.di

import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.dailyword.domain.DailyWordPicker
import com.speakmind.app.feature.dailyword.domain.DailyWordService
import com.speakmind.app.feature.dailyword.ui.WordDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dailyWordModule = module {
    single { DailyWordRepository(get()) }
    single { DailyWordPicker(get(), get()) }
    single { DailyWordService(get(), get(), get()) }
    viewModel { params ->
        WordDetailViewModel(params.get(), params.get(), get(), get(), get(), get())
    }
}
