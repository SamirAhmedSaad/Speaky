package com.speakmind.app.core.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun supabaseHttpEngine(): HttpClientEngine = Darwin.create()
