package com.speakmind.app.feature.streak

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.speakmind.app.compose.R
import io.github.aakira.napier.Napier
import java.util.Calendar

actual object StreakReminder {
    private const val REQUEST_CODE = 4711
    private const val HOUR = 19

    actual fun scheduleTomorrow(streak: Int) {
        if (streak < 2) return
        val context = org.koin.mp.KoinPlatform.getKoin().get<Context>()
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, StreakReminderReceiver::class.java).putExtra(StreakReminderReceiver.EXTRA_STREAK, streak)
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Tomorrow evening; replaced on every app open, so it only fires on a day without a visit.
        val at = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        try {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 30 * 60 * 1000L, pending)
        } catch (e: Exception) {
            Napier.w("Streak reminder not scheduled", e)
        }
    }
}

class StreakReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val streak = intent.getIntExtra(EXTRA_STREAK, 0)
        if (streak < 2) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Streak reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = Intent(context, Class.forName("com.speakmind.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 4712, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your $streak-day streak is in danger! ⚠️")
            .setContentText("Open Speaky today to keep it.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try { manager.notify(NOTIFICATION_ID, notification) } catch (e: SecurityException) { Napier.w("No notification permission", e) }
    }

    companion object {
        const val EXTRA_STREAK = "streak"
        private const val CHANNEL_ID = "streak_reminder"
        private const val NOTIFICATION_ID = 4713
    }
}
