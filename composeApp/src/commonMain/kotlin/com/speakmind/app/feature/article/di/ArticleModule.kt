package com.speakmind.app.feature.article.di

import com.speakmind.app.feature.article.ui.ArticleDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val articleModule = module {
    viewModel { params -> ArticleDetailViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
