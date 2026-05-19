package com.speakmind.app.feature.home.di

import com.speakmind.app.feature.home.data.DailyTopicCache
import com.speakmind.app.feature.home.data.NewsInLevelsRepository
import com.speakmind.app.feature.home.data.ScenarioRepository
import com.speakmind.app.feature.home.domain.DailyTopicSelector
import com.speakmind.app.feature.home.domain.DailyTopicService
import com.speakmind.app.feature.home.ui.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module {
    single { ScenarioRepository() }
    single { DailyTopicCache(get()) }
    single { DailyTopicSelector() }
    single { NewsInLevelsRepository(get(), get()) }
    single { DailyTopicService(get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
