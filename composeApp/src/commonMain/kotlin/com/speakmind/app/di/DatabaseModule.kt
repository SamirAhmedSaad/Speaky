package com.speakmind.app.di

import com.speakmind.app.db.SpeakMindDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single { SpeakMindDatabase(get()) }
}
