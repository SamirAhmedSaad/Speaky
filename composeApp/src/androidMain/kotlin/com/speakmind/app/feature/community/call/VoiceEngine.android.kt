package com.speakmind.app.feature.community.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

actual fun createVoiceEngine(provider: String): VoiceEngine {
    val context = CallKeepAlive.context()
    return if (provider == PROVIDER_LIVEKIT) LiveKitVoiceEngine(context) else AgoraVoiceEngine(context)
}

actual object CallKeepAlive : KoinComponent {
    fun context(): Context = get()

    actual fun start(title: String, text: String, muted: Boolean) {
        val context = context()
        // A microphone foreground service needs RECORD_AUDIO granted (Android 14+ throws SecurityException
        // otherwise). Without it the service could never go foreground, so don't start it at all.
        if (!HubCallService.hasMicPermission(context)) return
        val intent = HubCallService.intent(context, HubCallService.ACTION_SHOW, title, text, muted)
        // Android can refuse a foreground service start (app no longer in the foreground): the call
        // still works while the app is open, so never crash for it.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (e: Exception) {
            io.github.aakira.napier.Napier.w("Call service start refused", e)
        }
    }

    actual fun update(title: String, text: String, muted: Boolean) {
        val context = context()
        // Re-sending a start command to the service is blocked once the app is in the background, which
        // left the notification stuck on "Connecting…". Updating the notification itself always works.
        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(HubCallService.NOTIFICATION_ID, HubCallService.buildNotification(context, title, text, muted))
        } catch (e: Exception) {
            io.github.aakira.napier.Napier.w("Call notification update failed", e)
        }
    }

    actual fun stop() {
        val context = context()
        context.stopService(Intent(context, HubCallService::class.java))
        // update() may have posted the notification without the service running; never leave it behind.
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(HubCallService.NOTIFICATION_ID) }
    }
}

/**
 * Foreground service (microphone type) that keeps a Hub call running in the background or with the
 * screen locked, with an ongoing notification: tap to return, Mute and Leave buttons.
 */
class HubCallService : Service(), KoinComponent {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = get<HubCallManager>()
        com.speakmind.app.core.diagnostics.Breadcrumbs.log("call service: ${intent?.action?.substringAfterLast('.') ?: "restart"}")
        when (intent?.action) {
            ACTION_MUTE -> {
                manager.toggleMute()
                return START_NOT_STICKY
            }
            ACTION_LEAVE -> {
                manager.leave()
                return START_NOT_STICKY
            }
        }
        val notification = buildNotification(
            this,
            intent?.getStringExtra(EXTRA_TITLE) ?: "Hub voice call",
            intent?.getStringExtra(EXTRA_TEXT).orEmpty(),
            intent?.getBooleanExtra(EXTRA_MUTED, false) ?: false,
        )
        // A microphone foreground service may only start while the app is in the foreground; if Android
        // refuses (SecurityException / ForegroundServiceStartNotAllowedException), stop quietly instead of
        // crashing the whole app and ending the call.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            io.github.aakira.napier.Napier.w("Call foreground service refused", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_SHOW = "speaky.call.SHOW"
        const val ACTION_MUTE = "speaky.call.MUTE"
        const val ACTION_LEAVE = "speaky.call.LEAVE"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_MUTED = "muted"
        private const val CHANNEL = "hub_voice_call"
        const val NOTIFICATION_ID = 4321

        fun hasMicPermission(context: Context): Boolean =
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun buildNotification(context: Context, title: String, text: String, muted: Boolean): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Voice calls", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows while you are in a Hub voice call"
                        setShowBadge(false)
                    },
                )
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                PendingIntent.getActivity(context, 0, it, flags)
            }
            val mute = PendingIntent.getService(context, 1, Intent(context, HubCallService::class.java).setAction(ACTION_MUTE), flags)
            val leave = PendingIntent.getService(context, 2, Intent(context, HubCallService::class.java).setAction(ACTION_LEAVE), flags)
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(if (muted) "$text · muted" else text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(0, if (muted) "Unmute" else "Mute", mute)
                .addAction(0, "Leave", leave)
                .build()
        }

        fun intent(context: Context, action: String, title: String, text: String, muted: Boolean): Intent =
            Intent(context, HubCallService::class.java).setAction(action)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_MUTED, muted)
    }
}
