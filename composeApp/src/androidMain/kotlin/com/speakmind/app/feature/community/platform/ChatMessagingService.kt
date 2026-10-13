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
        val roomId = data["room_id"] ?: return
        // The chat is already open on screen.
        if (ActiveRoomTracker.activeRoomId.value == roomId) return
        val isHub = data["kind"] == "hub"
        showNotification(
            context = this,
            roomId = roomId,
            roomTitle = data["room_title"].orEmpty(),
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty(),
            channel = if (isHub) HUB_CHANNEL else CHANNEL_ID,
        )
    }

    companion object {
        const val EXTRA_ROOM_ID = "community_room_id"
        const val EXTRA_ROOM_TITLE = "community_room_title"
        /** Lets MainActivity ignore re-delivered (already handled) notification taps. */
        const val EXTRA_TAP_ID = "community_tap_id"
        private const val CHANNEL_ID = "community_messages"
        /** Speaky Hub alerts get their own, quieter channel that users can switch off in Android settings. */
        private const val HUB_CHANNEL = "speaky_hub"

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
            val openIntent = Intent(context, Class.forName("com.speakmind.app.MainActivity")).apply {
                // Reuse the running app (onNewIntent) instead of restarting it.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_ROOM_TITLE, roomTitle.ifEmpty { title })
                putExtra(EXTRA_TAP_ID, System.currentTimeMillis())
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                roomId.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, channel)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(if (channel == HUB_CHANNEL) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // One notification per chat; newer messages replace older ones.
            manager.notify(roomId.hashCode(), notification)
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
                        description = "One alert when Speaky Hub has new messages, until you open it"
                    }
                )
            }
        }
    }
}
