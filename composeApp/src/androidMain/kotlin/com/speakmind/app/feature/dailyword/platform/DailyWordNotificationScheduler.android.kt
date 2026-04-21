package com.speakmind.app.feature.dailyword.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.aakira.napier.Napier
import java.util.Calendar

actual class DailyWordNotificationScheduler(private val context: Context) {

    actual fun schedule(hour: Int, minute: Int, word: String, meaning: String): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, DailyWordAlarmReceiver::class.java).apply {
            putExtra(DailyWordAlarmReceiver.EXTRA_WORD, word)
            putExtra(DailyWordAlarmReceiver.EXTRA_MEANING, meaning)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DailyWordAlarmReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAtMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        Napier.d { "canScheduleExactAlarms: ${alarmManager.canScheduleExactAlarms()}, SDK: ${Build.VERSION.SDK_INT}" }

        return try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent,
                )
                Napier.d { "Daily word exact alarm scheduled for $hour:$minute" }
                false // no permission prompt needed
            } else {
                // Fallback: inexact window — still fires, just ±15 min
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    WINDOW_MS,
                    pendingIntent,
                )
                Napier.d { "Daily word inexact alarm scheduled for $hour:$minute (no exact permission)" }
                true // prompt user to grant exact alarm permission
            }
        } catch (e: Exception) {
            Napier.e { "Failed to schedule daily word alarm: ${e.message}" }
            false
        }
    }

    actual fun cancel() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyWordAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DailyWordAlarmReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        Napier.d { "Daily word alarm cancelled" }
    }

    companion object {
        private const val WINDOW_MS = 15 * 60 * 1000L
    }
}
