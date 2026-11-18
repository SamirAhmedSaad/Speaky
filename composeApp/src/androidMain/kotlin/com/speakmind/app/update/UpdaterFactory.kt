package com.speakmind.app.update

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager

/**
 * The real Google Play updater, or, in debug builds started with a test extra, a simulated one so the
 * whole flow can be tried on an emulator without publishing to Play:
 *
 *   adb shell am start -n com.speaky_ai.app/com.speakmind.app.MainActivity --ei fake_update 1   (optional)
 *   adb shell am start -n com.speaky_ai.app/com.speakmind.app.MainActivity --ei fake_update 2   (mandatory)
 *   adb shell am start -n com.speaky_ai.app/com.speakmind.app.MainActivity --ei fake_update 3   (optional, obeys the admin switch)
 *
 * Mode 2 also sets the minimum version above this build, as the server would.
 */
fun createUpdater(activity: ComponentActivity, debug: Boolean): PlayAppUpdater {
    val mode = if (debug) activity.intent?.getIntExtra("fake_update", 0) ?: 0 else 0
    if (mode == 0) return PlayAppUpdater(activity)

    val current = runCatching {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(1L)
    // Mode 3: simulated Play, but the server's on/off switch decides (tests the admin switch).
    if (mode != 3) {
        AppUpdate.forceEnabledForTest = true
        AppUpdate.setEnabled(true)
    }
    val fake = FakeAppUpdateManager(activity)
    fake.setUpdateAvailable((current + 1).toInt())
    if (mode == 2) AppUpdate.applyServerSettings(current + 1, optionalOn = true, message = null, enabled = true)
    FakePlay(fake).start()
    return PlayAppUpdater(activity, fake)
}

/** Plays the part of Google Play for [FakeAppUpdateManager]: accepts dialogs and "downloads" in ~3 s. */
private class FakePlay(private val fake: FakeAppUpdateManager) {
    private val main = Handler(Looper.getMainLooper())
    private var downloading = false

    fun start() = main.postDelayed(::tick, 300)

    private fun tick() {
        when {
            fake.isImmediateFlowVisible -> {
                fake.userAcceptsUpdate()
                fake.downloadStarts()
                fake.downloadCompletes()
                fake.installCompletes()
                android.util.Log.i("FakeUpdate", "Immediate (mandatory) update installed")
            }
            fake.isConfirmationDialogVisible -> {
                fake.userAcceptsUpdate()
                fake.downloadStarts()
                downloading = true
                simulateProgress(0)
            }
            fake.isInstallSplashScreenVisible -> {
                fake.installCompletes()
                android.util.Log.i("FakeUpdate", "Flexible (optional) update installed")
            }
        }
        main.postDelayed(::tick, 300)
    }

    private fun simulateProgress(step: Int) {
        if (!downloading) return
        if (step >= 10) {
            downloading = false
            fake.downloadCompletes()
            return
        }
        fake.setTotalBytesToDownload(100)
        fake.setBytesDownloaded((step * 10).toLong())
        main.postDelayed({ simulateProgress(step + 1) }, 300)
    }

}
