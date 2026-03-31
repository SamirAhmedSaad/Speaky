package com.speakmind.app

import android.app.Application
import com.speakmind.app.di.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext

class SpeakMindApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
        initKoin { androidContext(this@SpeakMindApplication) }
    }
}
