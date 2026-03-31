package com.speakmind.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.speakmind.app.db.SpeakMindDatabase
import com.speakmind.app.feature.ai.platform.AndroidModelDownloader
import com.speakmind.app.feature.ai.platform.ModelDownloader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appModule: Module
    get() = module {
        single<SqlDriver> {
            AndroidSqliteDriver(
                SpeakMindDatabase.Schema,
                androidContext(),
                "speakmind.db"
            )
        }
        single<ModelDownloader> { AndroidModelDownloader(androidContext()) }
    }
