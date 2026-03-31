package com.speakmind.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.speakmind.app.db.SpeakMindDatabase
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.feature.ai.platform.NoOpModelDownloader
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appModule: Module
    get() = module {
        single<SqlDriver> {
            NativeSqliteDriver(
                SpeakMindDatabase.Schema,
                "speakmind.db"
            )
        }
        single<ModelDownloader> { NoOpModelDownloader() }
    }
