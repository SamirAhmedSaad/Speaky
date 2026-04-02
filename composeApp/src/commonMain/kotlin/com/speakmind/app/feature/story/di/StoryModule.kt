package com.speakmind.app.feature.story.di

import com.speakmind.app.feature.story.data.StoryRepository
import com.speakmind.app.feature.story.ui.StoryDetailViewModel
import com.speakmind.app.feature.story.ui.StoryViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val storyModule = module {
    single { HttpClient() }
    single { StoryRepository(get(), get()) }
    viewModel { StoryViewModel(get(), get()) }
    viewModel { params -> StoryDetailViewModel(params.get(), get(), get(), get(), get()) }
}
