package com.speakmind.app.update

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.github.aakira.napier.Napier

/**
 * Google Play In-App Updates. Create it in the activity's onCreate (it registers an activity
 * result), call [check] after the server settings load and in onResume.
 *
 * Every Play call is wrapped: a phone without Play services, an app installed outside Play or any
 * Play error simply means "no update" and the app keeps working.
 */
class PlayAppUpdater(
    private val activity: ComponentActivity,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
) : AppUpdate.Platform {

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // Cancelled or failed: check again, which keeps the mandatory screen up (with its button).
            if (result.resultCode != android.app.Activity.RESULT_OK) {
                Napier.i { "Update flow result ${result.resultCode}" }
                // Declined Play's optional-update dialog: back to the banner, not a stuck "Downloading".
                if (AppUpdate.ui.value is AppUpdate.Ui.Downloading) AppUpdate.setUi(AppUpdate.Ui.Available)
            }
            check()
        }

    private var lastInfo: AppUpdateInfo? = null
    private var listening = false

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload()
                val percent = if (total > 0) (state.bytesDownloaded() * 100 / total).toInt().coerceIn(0, 100) else 0
                if (AppUpdate.ui.value != AppUpdate.Ui.Required) AppUpdate.setUi(AppUpdate.Ui.Downloading(percent))
            }
            InstallStatus.DOWNLOADED -> if (AppUpdate.ui.value != AppUpdate.Ui.Required) AppUpdate.setUi(AppUpdate.Ui.ReadyToInstall)
            InstallStatus.FAILED, InstallStatus.CANCELED -> {
                if (AppUpdate.ui.value is AppUpdate.Ui.Downloading) AppUpdate.setUi(AppUpdate.Ui.Available)
            }
            else -> Unit
        }
    }

    init {
        AppUpdate.platform = this
    }

    fun register() {
        if (listening) return
        runCatching { manager.registerListener(installListener) }.onSuccess { listening = true }
    }

    fun unregister() {
        if (!listening) return
        runCatching { manager.unregisterListener(installListener) }
        listening = false
    }

    private val currentVersionCode: Long by lazy {
        runCatching {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        }.getOrDefault(Long.MAX_VALUE) // unknown: never force anything
    }

    /** Asks Play about updates and shows the right prompt. Safe to call often. */
    fun check() {
        val task = try {
            manager.appUpdateInfo
        } catch (e: Exception) {
            Napier.w("Update check unavailable", e)
            return
        }
        task.addOnSuccessListener { info -> onInfo(info) }
            .addOnFailureListener { Napier.w("Update check failed", it) }
    }

    private fun onInfo(info: AppUpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return
        lastInfo = info

        // An optional update already downloaded: offer the restart.
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            AppUpdate.setUi(AppUpdate.Ui.ReadyToInstall)
            return
        }

        val availability = info.updateAvailability()
        val decision = decideUpdate(
            enabled = AppUpdate.enabled.value,
            currentVersionCode = currentVersionCode,
            minVersionCode = AppUpdate.minVersionCode,
            optionalOn = AppUpdate.optionalOn,
            playHasUpdate = availability == UpdateAvailability.UPDATE_AVAILABLE ||
                availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
        )

        // A mandatory update was started and the app was left meanwhile: resume Play's screen.
        if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS && decision == UpdateDecision.Required) {
            AppUpdate.setUi(AppUpdate.Ui.Required)
            startFlow(info, AppUpdateType.IMMEDIATE)
            return
        }
        // A background download in progress.
        if (info.installStatus() == InstallStatus.DOWNLOADING || info.installStatus() == InstallStatus.PENDING) {
            if (decision != UpdateDecision.Required) {
                if (AppUpdate.ui.value !is AppUpdate.Ui.Downloading) AppUpdate.setUi(AppUpdate.Ui.Downloading(0))
                return
            }
        }

        when (decision) {
            UpdateDecision.Required -> {
                val first = AppUpdate.ui.value != AppUpdate.Ui.Required
                AppUpdate.setUi(AppUpdate.Ui.Required)
                // Open Play's update screen straight away the first time; after a cancel, the
                // full-screen prompt stays with its Update button.
                if (first) startFlow(info, AppUpdateType.IMMEDIATE)
            }
            UpdateDecision.Optional -> {
                val ui = AppUpdate.ui.value
                if (!AppUpdate.isOptionalDismissed && (ui == AppUpdate.Ui.None || ui == AppUpdate.Ui.Required)) {
                    AppUpdate.setUi(AppUpdate.Ui.Available)
                }
            }
            UpdateDecision.None -> {
                // Never leave a stale prompt (e.g. the server lowered the minimum version).
                if (AppUpdate.ui.value == AppUpdate.Ui.Required || AppUpdate.ui.value == AppUpdate.Ui.Available) {
                    AppUpdate.setUi(AppUpdate.Ui.None)
                }
            }
        }
    }

    private fun startFlow(info: AppUpdateInfo, type: Int) {
        com.speakmind.app.core.diagnostics.Breadcrumbs.log("update: start ${if (type == AppUpdateType.IMMEDIATE) "mandatory" else "optional"}")
        try {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
        } catch (e: Exception) {
            Napier.w("Update flow failed to start", e)
        }
    }

    override fun startRequired() {
        val info = lastInfo
        if (info == null) {
            check()
            return
        }
        // AppUpdateInfo can only be used once: fetch fresh info, then start.
        runCatching { manager.appUpdateInfo }.getOrNull()
            ?.addOnSuccessListener { fresh -> startFlow(fresh, AppUpdateType.IMMEDIATE) }
            ?.addOnFailureListener { Napier.w("Update check failed", it) }
    }

    override fun startOptional() {
        register()
        runCatching { manager.appUpdateInfo }.getOrNull()
            ?.addOnSuccessListener { fresh ->
                if (fresh.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    AppUpdate.setUi(AppUpdate.Ui.Downloading(0))
                    startFlow(fresh, AppUpdateType.FLEXIBLE)
                }
            }
            ?.addOnFailureListener { Napier.w("Update check failed", it) }
    }

    override fun installDownloaded() {
        runCatching { manager.completeUpdate() }.onFailure { Napier.w("Install failed", it) }
    }
}
