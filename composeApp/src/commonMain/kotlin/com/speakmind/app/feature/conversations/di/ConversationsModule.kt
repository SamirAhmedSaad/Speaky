package com.speakmind.app.feature.conversations.di

import com.speakmind.app.feature.conversations.ui.ConversationListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val conversationsModule = module {
    viewModel { ConversationListViewModel(get(), get()) }
}
