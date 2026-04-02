package com.speakmind.app.feature.voice.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class MicPermissionRequester {

    private var launcher: ActivityResultLauncher<String>? = null

    fun register(launcher: ActivityResultLauncher<String>) {
        this.launcher = launcher
    }

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /** Returns true if already granted, false if a permission dialog was launched. */
    fun requestIfNeeded(context: Context): Boolean {
        return if (isGranted(context)) {
            true
        } else {
            launcher?.launch(Manifest.permission.RECORD_AUDIO)
            false
        }
    }
}
