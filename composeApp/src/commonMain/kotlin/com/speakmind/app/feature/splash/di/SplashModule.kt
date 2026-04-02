package com.speakmind.app.feature.splash.di

import com.speakmind.app.feature.splash.ui.SplashViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val splashModule = module {
    viewModel { SplashViewModel(get(), get()) }
}
