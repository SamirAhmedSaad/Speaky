package com.speakmind.app.feature.community.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.speakmind.app.compose.R
import com.speakmind.app.feature.community.data.ActiveRoomTracker
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin

/** Shows notifications for new group / private chat messages (sent by the push-notify Edge Function). */
class ChatMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            try {
                getKoin().get<CommunityRepository>().registerPushToken(token)
            } catch (e: Exception) {
                Napier.w("Push token registration failed", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Napier.i("push received: ${data["kind"]}")
        // Admins and moderators: someone reported a user.
        if (data["kind"] == "report") {
            showReportNotification(this, data["title"].orEmpty(), data["body"].orEmpty())
            return
        }
        val roomId = data["room_id"] ?: return
        // Someone started a Hub voice call (shown even when the Hub is open: it's an invitation).
        if (data["kind"] == "hub_call") {
            runCatching {
                showCallNotification(this, roomId, data["room_title"].orEmpty(), data["call_id"].orEmpty(), data["title"].orEmpty(), data["body"].orEmpty())
            }.onFailure { Napier.w("call notification failed", it) }
            return
        }
        // The chat is already open on screen.
        if (ActiveRoomTracker.activeRoomId.value == roomId) return
        if (data["kind"] == "hub") {
            showHubNotification(
                context = this,
                roomId = roomId,
                roomTitle = data["room_title"].orEmpty(),
                title = data["title"].orEmpty(),
                line = data["body"].orEmpty(),
                count = data["count"]?.toIntOrNull() ?: 1,
            )
            return
        }
        showNotification(
            context = this,
            roomId = roomId,
            roomTitle = data["room_title"].orEmpty(),
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty(),
            channel = CHANNEL_ID,
        )
    }

    companion object {
        const val EXTRA_ROOM_ID = "community_room_id"
        const val EXTRA_ROOM_TITLE = "community_room_title"
        /** Report notification: open the Reports screen. */
        const val EXTRA_OPEN_REPORTS = "community_open_reports"
        /** Voice call invitation: the call to check on tap (it may have ended by then). */
        const val EXTRA_CALL_ID = "community_call_id"
        private const val CALL_NOTIFICATION_ID = 0x5EA4_0003
        private const val REPORT_NOTIFICATION_ID = 0x5EA4_0002
        /** Lets MainActivity ignore re-delivered (already handled) notification taps. */
        const val EXTRA_TAP_ID = "community_tap_id"
        private const val CHANNEL_ID = "community_messages"
        /** Speaky Hub alerts get their own, quieter channel that users can switch off in Android settings. */
        private const val HUB_CHANNEL = "speaky_hub"

        private const val HUB_NOTIFICATION_ID = 0x5EA4_0001
        private const val HUB_PREFS = "hub_notification"
        private const val HUB_MAX_LINES = 5

        /**
         * Speaky Hub: one notification that grows with each new message (up to 5 lines). Only the
         * first one makes a sound; later ones update it silently. Cleared when the Hub is opened.
         */
        private fun showHubNotification(context: Context, roomId: String, roomTitle: String, title: String, line: String, count: Int) {
            val prefs = context.getSharedPreferences(HUB_PREFS, Context.MODE_PRIVATE)
            val lines = (prefs.getString("lines", "").orEmpty().split('\n').filter { it.isNotBlank() } + line.replace('\n', ' '))
                .takeLast(HUB_MAX_LINES)
            // commit(), not apply(): this runs on FCM's background thread. A pending apply() is flushed on
            // the MAIN thread when the service stops (QueuedWork.waitToFinish), which caused ANRs on slow storage.
            prefs.edit().putString("lines", lines.joinToString("\n")).commit()
            val summary = if (count >= HUB_MAX_LINES) "$HUB_MAX_LINES+ new messages" else if (count > 1) "$count new messages" else "New message"
            val style = NotificationCompat.InboxStyle().setSummaryText(summary)
            lines.forEach { style.addLine(it) }
            val notification = NotificationCompat.Builder(context, HUB_CHANNEL)
                .setContentTitle(title)
                .setContentText(if (lines.size > 1) "$summary · ${lines.last()}" else line)
                .setStyle(style)
                .setNumber(count)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openChatIntent(context, roomId, roomTitle.ifEmpty { title }))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            createChannels(context)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(HUB_NOTIFICATION_ID, notification)
        }

        /** The user opened this private chat: its notification is read. */
        fun clearChatNotification(context: Context, roomId: String) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(roomId.hashCode())
        }

        /** The user opened Speaky Hub: remove its notification and start the next one fresh. */
        fun clearHubNotification(context: Context) {
            context.getSharedPreferences(HUB_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(HUB_NOTIFICATION_ID)
        }

        private fun openChatIntent(context: Context, roomId: String, roomTitle: String): PendingIntent {
            val openIntent = Intent(context, Class.forName("com.speakmind.app.MainActivity")).apply {
                // Reuse the running app (onNewIntent) instead of restarting it.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_ROOM_TITLE, roomTitle)
                putExtra(EXTRA_TAP_ID, System.currentTimeMillis())
            }
            return PendingIntent.getActivity(
                context,
                roomId.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** Fetches the current FCM token and saves it for the signed-in user. */
        fun registerCurrentToken() {
            try {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            getKoin().get<CommunityRepository>().registerPushToken(token)
                        } catch (e: Exception) {
                            Napier.w("Push token registration failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.w("FCM unavailable", e)
            }
        }

        private fun showNotification(
            context: Context,
            roomId: String,
            roomTitle: String,
            title: String,
            body: String,
            channel: String,
        ) {
            createChannels(context)
            val pendingIntent = openChatIntent(context, roomId, roomTitle.ifEmpty { title })
            val notification = NotificationCompat.Builder(context, channel)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // One notification per chat; newer messages replace older ones.
            manager.notify(roomId.hashCode(), notification)
        }

        /** "🎙️ Voice call in Speaky Hub": tap opens the Hub, which joins or says the call ended. */
        private fun showCallNotification(context: Context, roomId: String, roomTitle: String, callId: String, title: String, body: String) {
            createChannels(context)
            val openIntent = Intent(context, Class.forName("com.speakmind.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_ROOM_TITLE, roomTitle)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_TAP_ID, System.currentTimeMillis())
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                CALL_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, HUB_CHANNEL)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                // Gone after 5 minutes: by then a call nobody joined has closed.
                .setTimeoutAfter(5 * 60 * 1000L)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(CALL_NOTIFICATION_ID, notification)
        }

        /** "🚩 New report": one notification, replaced by newer reports; tapping it opens Reports. */
        private fun showReportNotification(context: Context, title: String, body: String) {
            createChannels(context)
            val openIntent = Intent(context, Class.forName("com.speakmind.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_REPORTS, true)
                putExtra(EXTRA_TAP_ID, System.currentTimeMillis())
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REPORT_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(REPORT_NOTIFICATION_ID, notification)
        }

        private fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Private messages", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "New private messages and replies to you"
                    }
                )
                manager.createNotificationChannel(
                    NotificationChannel(HUB_CHANNEL, "Speaky Hub", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "One notification with the latest Speaky Hub messages, until you open it"
                    }
                )
            }
        }
    }
}
