package com.speakmind.app.feature.auth.di

import com.speakmind.app.feature.auth.domain.AuthRepository
import com.speakmind.app.feature.auth.ui.LoginViewModel
import com.speakmind.app.feature.auth.ui.RegisterViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    single { AuthRepository(get()) }
    viewModel { LoginViewModel(get(), get()) }
    viewModel { RegisterViewModel(get(), get()) }
}
