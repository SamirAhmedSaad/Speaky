package com.speakmind.app.feature.ai.di

import com.speakmind.app.feature.ai.platform.AiEngineProvider
import com.speakmind.app.feature.ai.platform.ModelPreloader
import com.speakmind.app.feature.geminichat.data.GeminiRepository
import org.koin.dsl.module

val aiModule = module {
    single { ModelPreloader(get()) }
    single { GeminiRepository(get()) }
    single { AiEngineProvider(get(), get(), get(), get(), get()) }
}
