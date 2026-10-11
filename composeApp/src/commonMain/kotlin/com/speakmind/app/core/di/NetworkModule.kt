package com.speakmind.app.core.di

import com.speakmind.app.core.network.BackendService
import com.speakmind.app.core.network.RealtimeService
import com.speakmind.app.core.network.StorageService
import com.speakmind.app.core.network.impl.SupabaseBackendService
import com.speakmind.app.core.network.impl.SupabaseRealtimeService
import com.speakmind.app.core.network.impl.SupabaseStorageService
import org.koin.dsl.module

val networkModule = module {
    single<BackendService> { SupabaseBackendService(get()) }
    single<StorageService> { SupabaseStorageService(get()) }
    single<RealtimeService> { SupabaseRealtimeService(get()) }
}
