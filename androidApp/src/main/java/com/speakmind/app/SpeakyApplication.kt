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

class SpeakyApplication : Application(), androidx.work.Configuration.Provider {

    /**
     * On some phones the storage is in a bad state and WorkManager can't open its own database; by
     * default that crashed the whole app on start (SQLITE_CANTOPEN). Log it instead: background jobs are
     * simply skipped on that phone and the app keeps working.
     */
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setInitializationExceptionHandler { e ->
                Napier.w("WorkManager unavailable", e)
                com.speakmind.app.core.diagnostics.Breadcrumbs.log("workmanager: init failed ${e::class.simpleName}")
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Google Play's pre-launch report runs the app on Firebase Test Lab robots, which open internal
        // screens directly (e.g. Play Billing's ProxyBillingActivity with no purchase data) and crash
        // inside Google's code. Those aren't real users: keep them out of Crashlytics.
        if (isFirebaseTestLab()) {
            runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false }
        }
        // What the app was doing (screen, call, background work) goes into every crash/ANR report.
        com.speakmind.app.core.diagnostics.Breadcrumbs.sink = CrashlyticsBreadcrumbs(this)
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

    /** Firebase Test Lab (used by Play's pre-launch report) marks its devices with this system setting. */
    private fun isFirebaseTestLab(): Boolean = runCatching {
        android.provider.Settings.System.getString(contentResolver, "firebase.test.lab") == "true"
    }.getOrDefault(false)
}

/** Sends breadcrumbs to Crashlytics (logs + keys) and screen views to Analytics (shown as breadcrumbs). */
private class CrashlyticsBreadcrumbs(app: Application) : com.speakmind.app.core.diagnostics.Breadcrumbs.Sink {
    private val crashlytics = runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance() }.getOrNull()
    private val analytics = runCatching { com.google.firebase.analytics.FirebaseAnalytics.getInstance(app) }.getOrNull()

    override fun log(message: String) {
        crashlytics?.log(message)
    }

    override fun key(name: String, value: String) {
        crashlytics?.setCustomKey(name, value)
    }

    override fun screen(name: String) {
        analytics?.logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SCREEN_VIEW, android.os.Bundle().apply {
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_NAME, name)
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_CLASS, name)
        })
    }
}
