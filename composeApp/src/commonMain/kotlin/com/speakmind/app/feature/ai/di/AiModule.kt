package com.speakmind.app.feature.ai.di

import org.koin.dsl.module

val aiModule = module {
    // LlmEngine and ModelFileManager will be provided by platform modules
    // when llama.cpp integration is ready
    // single { LlmEngine() }
    // single { ModelFileManager() }
}
