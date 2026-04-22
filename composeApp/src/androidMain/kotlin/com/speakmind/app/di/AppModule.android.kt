package com.speakmind.app.di

import app.cash.sqldelight.db.SqlDriver
import com.speakmind.app.feature.geminichat.data.ApiKeyStore
import com.speakmind.app.feature.ai.platform.AndroidModelDownloader
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.feature.dailyword.platform.DailyWordNotificationScheduler
import com.speakmind.app.feature.voice.platform.MicPermissionRequester
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appModule: Module
    get() = module {
        single<SqlDriver> { createSpeakMindDriver(androidContext()) }
        single<ModelDownloader> { AndroidModelDownloader(androidContext()) }
        single { DailyWordNotificationScheduler(androidContext()) }
        single { ApiKeyStore(androidContext()) }
        single { MicPermissionRequester() }
    }
