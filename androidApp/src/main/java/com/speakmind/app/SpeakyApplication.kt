package com.speakmind.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.speakmind.app.ads.AdConfig
import com.speakmind.app.ads.AdManager
import com.speakmind.app.di.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import network.chaintech.sdpcomposemultiplatform.SDPConfig
import org.koin.android.ext.koin.androidContext

class SpeakyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SDPConfig.setScalingRatio(360.0)
        Napier.base(DebugAntilog())

        // Master switch — set to true when ready to enable ads
        AdConfig.adsEnabled = false
        // Use test ads in debug builds to avoid account bans
        AdConfig.useTestAds = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        initKoin { androidContext(this@SpeakyApplication) }
        AdManager.init(this)
    }
}
