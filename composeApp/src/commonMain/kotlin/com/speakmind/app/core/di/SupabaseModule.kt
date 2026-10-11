package com.speakmind.app.core.di

import com.speakmind.app.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module

val supabaseModule = module {
    single<SupabaseClient> {
        createSupabaseClient(
            supabaseUrl = AppConfig.supabaseUrl,
            supabaseKey = AppConfig.supabaseAnonKey,
        ) {
            httpEngine = supabaseHttpEngine()
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}

/** HTTP engine with WebSocket support (needed by Supabase Realtime). */
expect fun supabaseHttpEngine(): HttpClientEngine
