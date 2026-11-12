package com.speakmind.app

import android.app.Application
import android.content.pm.ApplicationInfo
import app.cash.sqldelight.db.SqlDriver
import com.speakmind.app.ads.AdConfig
import com.speakmind.app.di.initKoin
import android.util.Log
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.chaintech.sdpcomposemultiplatform.SDPConfig
import org.koin.android.ext.koin.androidContext
import org.koin.java.KoinJavaComponent.getKoin

/** Release logging: warnings and errors only, fixed tag, no stack inspection. */
private class ReleaseAntilog : Antilog() {
    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        if (priority < LogLevel.WARNING) return
        val text = message.orEmpty()
        when (priority) {
            LogLevel.WARNING -> Log.w(tag ?: "Speaky", text, throwable)
            else -> Log.e(tag ?: "Speaky", text, throwable)
        }
    }
}

class SpeakyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SDPConfig.setScalingRatio(360.0)
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // DebugAntilog reads a fixed stack-trace depth to build its tag; R8 inlining makes the stack
        // shorter, so every Napier.w/e crashed with ArrayIndexOutOfBoundsException in shrunk builds.
        // Only the plain (unshrunk) debug build may use it; release and minitest (shrunk + debuggable) can't.
        Napier.base(if (BuildConfig.BUILD_TYPE == "debug") DebugAntilog() else ReleaseAntilog())
        AdConfig.adsEnabled = true
        AdConfig.useTestAds = isDebug

        initKoin { androidContext(this@SpeakyApplication) }

        // Pre-warm the DB driver on IO so the first activity never runs
        // createSpeakMindDriver() (disk I/O) on the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            getKoin().get<SqlDriver>()
        }

        // The on-device AI chat is off: free the old downloaded model (about 1 GB) and stop any
        // download still queued from an older version.
        if (!com.speakmind.app.config.Features.AI_CHAT) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    java.io.File(filesDir, "models").takeIf { it.exists() }?.deleteRecursively()
                    getExternalFilesDir(null)?.let { java.io.File(it, "models") }?.takeIf { it.exists() }?.deleteRecursively()
                    androidx.work.WorkManager.getInstance(this@SpeakyApplication).cancelUniqueWork("model_download")
                }
            }
        }
    }
}
