package com.speakmind.app.feature.onboarding.di

import com.speakmind.app.feature.onboarding.ui.OnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val onboardingModule = module {
    viewModel { OnboardingViewModel(get(), get(), get()) }
}
