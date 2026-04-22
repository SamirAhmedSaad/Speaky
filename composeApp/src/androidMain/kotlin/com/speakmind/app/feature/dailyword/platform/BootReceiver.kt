package com.speakmind.app.feature.dailyword.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.di.createSpeakMindDriver
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Napier.d { "Boot completed — rescheduling daily word alarm" }

        try {
            val driver = createSpeakMindDriver(context)
            val database = SpeakyDatabase(driver)

            val settings = database.speakMindQueries.selectSettings().executeAsOneOrNull()
            if (settings == null || settings.notifications_enabled == 0L) {
                driver.close()
                return
            }

            // Get the most recent word for notification content
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            val recentWord = database.speakMindQueries.selectRecentDailyWords(today)
                .executeAsList().firstOrNull()

            if (recentWord != null) {
                val scheduler = DailyWordNotificationScheduler(context)
                scheduler.schedule(
                    hour = settings.notification_hour.toInt(),
                    minute = settings.notification_minute.toInt(),
                    word = recentWord.word,
                    meaning = recentWord.meaning,
                )
            }

            driver.close()
        } catch (e: Exception) {
            Napier.e { "Failed to reschedule after boot: ${e.message}" }
        }
    }
}
