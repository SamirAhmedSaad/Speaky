package com.speakmind.app.di

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.ui.theme.ThemeManager
import com.speakmind.app.ui.theme.TtsSpeedManager
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single { Json { ignoreUnknownKeys = true } }
    single { SpeakyDatabase(get()) }
    single { ThemeManager(get()) }
    single { TtsSpeedManager(get()) }
}
