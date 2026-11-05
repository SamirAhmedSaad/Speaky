package com.speakmind.app.feature.games.di

import com.speakmind.app.feature.games.data.GameRepository
import com.speakmind.app.feature.games.data.IdiomRepository
import com.speakmind.app.feature.games.ui.GameLevelsViewModel
import com.speakmind.app.feature.games.ui.GamesViewModel
import com.speakmind.app.feature.games.ui.IdiomBuilderViewModel
import com.speakmind.app.feature.games.ui.IdiomRushViewModel
import com.speakmind.app.feature.games.ui.WordGameViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val gamesModule = module {
    single { IdiomRepository(get(), get()) }
    single { GameRepository(get(), get(), get()) }
    viewModel { GamesViewModel(get(), get()) }
    viewModel { params -> GameLevelsViewModel(params.get(), get(), get()) }
    viewModel { params -> WordGameViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> IdiomRushViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> IdiomBuilderViewModel(params.get(), get(), get(), get()) }
}
