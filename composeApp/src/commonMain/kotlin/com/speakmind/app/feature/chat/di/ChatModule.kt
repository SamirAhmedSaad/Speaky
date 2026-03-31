package com.speakmind.app.feature.chat.di

import com.speakmind.app.feature.chat.ui.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val chatModule = module {
    viewModel { params -> ChatViewModel(params.getOrNull(), get(), get(), get(), get()) }
}
