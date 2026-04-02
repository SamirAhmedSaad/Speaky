package com.speakmind.app.feature.download.di

import com.speakmind.app.feature.download.ui.ModelDownloadViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val downloadModule = module {
    viewModel { params -> ModelDownloadViewModel(params.getOrNull(), get(), get(), get(), get()) }
}
