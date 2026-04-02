package com.speakmind.app.feature.aisetup.di

import com.speakmind.app.feature.aisetup.ui.AiSetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aiSetupModule = module {
    viewModel { params ->
        AiSetupViewModel(
            scenarioId = params.getOrNull(),
            navigationManager = get(),
            apiKeyStore = get(),
            geminiRepository = get(),
            modelDownloader = get(),
            database = get(),
        )
    }
}
