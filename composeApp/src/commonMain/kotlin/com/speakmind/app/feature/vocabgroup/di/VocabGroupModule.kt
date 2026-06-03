package com.speakmind.app.feature.vocabgroup.di

import com.speakmind.app.feature.vocabgroup.data.VocabGroupRepository
import com.speakmind.app.feature.vocabgroup.ui.GroupDetailViewModel
import com.speakmind.app.feature.vocabgroup.ui.MyGroupsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val vocabGroupModule = module {
    single { VocabGroupRepository(get(), get()) }
    viewModel { MyGroupsViewModel(get(), get()) }
    viewModel { params -> GroupDetailViewModel(params.get(), params.get(), get(), get(), get(), get(), get(), get(), get()) }
}
