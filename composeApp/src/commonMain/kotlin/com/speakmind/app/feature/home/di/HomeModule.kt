package com.speakmind.app.feature.home.di

import com.speakmind.app.feature.home.data.ScenarioRepository
import com.speakmind.app.feature.home.ui.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module {
    single { ScenarioRepository() }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
}
