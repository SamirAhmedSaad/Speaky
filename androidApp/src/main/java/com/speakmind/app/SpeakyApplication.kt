package com.speakmind.app

import android.app.Application
import android.content.pm.ApplicationInfo
import app.cash.sqldelight.db.SqlDriver
import com.speakmind.app.ads.AdConfig
import com.speakmind.app.ads.AdManager
import com.speakmind.app.di.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.chaintech.sdpcomposemultiplatform.SDPConfig
import org.koin.android.ext.koin.androidContext
import org.koin.java.KoinJavaComponent.getKoin

class SpeakyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SDPConfig.setScalingRatio(360.0)
        Napier.base(DebugAntilog())

        AdConfig.adsEnabled = false
        AdConfig.useTestAds = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        initKoin { androidContext(this@SpeakyApplication) }

        // Pre-warm the DB driver on IO so the first activity never runs
        // createSpeakMindDriver() (disk I/O) on the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            getKoin().get<SqlDriver>()
        }

        AdManager.init(this)
    }
}
