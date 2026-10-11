package com.speakmind.app.core.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun supabaseHttpEngine(): HttpClientEngine = OkHttp.create()
