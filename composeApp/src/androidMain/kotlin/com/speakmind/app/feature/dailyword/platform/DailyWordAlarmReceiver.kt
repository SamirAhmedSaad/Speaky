package com.speakmind.app.feature.dailyword.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.speakmind.app.compose.R
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.di.createSpeakMindDriver
import com.speakmind.app.feature.vocabulary.domain.model.VocabularyData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class DailyWordAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_WORD = "daily_word"
        const val EXTRA_MEANING = "daily_meaning"
        const val REQUEST_CODE = 2001
        private const val CHANNEL_ID = "daily_word_channel"
        private const val NOTIFICATION_ID = 2002
        private const val VOCAB_ASSET_PATH =
            "composeResources/speaky.composeapp.generated.resources/files/vocabulary.json"
        private val LEVEL_ORDER = listOf("A1", "A2", "B1", "B2", "C1")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleDailyWordAlarm(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDailyWordAlarm(context: Context, intent: Intent) {
        val driver = createSpeakMindDriver(context)
        val database = SpeakyDatabase(driver)
        try {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

            // Get today's word from DB, or pick and insert a new one if the app hasn't been opened
            var todayRow = database.speakMindQueries.selectTodayWord(today).executeAsOneOrNull()
            if (todayRow == null) {
                val userLevel = database.speakMindQueries.selectProgress()
                    .executeAsOneOrNull()?.level ?: "A2"
                val sentWords = database.speakMindQueries.selectAllSentWords().executeAsList().toSet()
                val newWord = pickWordFromAssets(context, sentWords, userLevel)
                if (newWord != null) {
                    val sentencesJson = Json.encodeToString(
                        ListSerializer(String.serializer()), newWord.sentences,
                    )
                    database.speakMindQueries.insertDailyWord(
                        word = newWord.word,
                        level = userLevel,
                        part_of_speech = newWord.partOfSpeech,
                        meaning = newWord.meaning,
                        sentences_json = sentencesJson,
                        sent_date = today,
                        is_read = 0,
                        created_at = Clock.System.now().toEpochMilliseconds(),
                    )
                    todayRow = database.speakMindQueries.selectTodayWord(today).executeAsOneOrNull()
                }
            }

            val word = todayRow?.word ?: intent.getStringExtra(EXTRA_WORD) ?: return
            val meaning = todayRow?.meaning ?: intent.getStringExtra(EXTRA_MEANING) ?: ""

            Napier.d { "Daily word alarm received: $word" }
            createNotificationChannel(context)
            showNotification(context, word, meaning)

            // Reschedule alarm for tomorrow so notifications keep firing without app launch
            val settings = database.speakMindQueries.selectSettings().executeAsOneOrNull()
            if (settings != null && settings.notifications_enabled != 0L) {
                val userLevel = database.speakMindQueries.selectProgress()
                    .executeAsOneOrNull()?.level ?: "A2"
                preFillTomorrowWord(context, database, userLevel)
                DailyWordNotificationScheduler(context).schedule(
                    hour = settings.notification_hour.toInt(),
                    minute = settings.notification_minute.toInt(),
                    word = word,
                    meaning = meaning,
                )
            }
        } finally {
            driver.close()
        }
    }

    private fun preFillTomorrowWord(context: Context, database: SpeakyDatabase, userLevel: String) {
        val tomorrow = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(1, DateTimeUnit.DAY).toString()
        if (database.speakMindQueries.selectTodayWord(tomorrow).executeAsOneOrNull() != null) return
        val sentWords = database.speakMindQueries.selectAllSentWords().executeAsList().toSet()
        val nextWord = pickWordFromAssets(context, sentWords, userLevel) ?: return
        val sentencesJson = Json.encodeToString(ListSerializer(String.serializer()), nextWord.sentences)
        database.speakMindQueries.insertDailyWord(
            word = nextWord.word,
            level = userLevel,
            part_of_speech = nextWord.partOfSpeech,
            meaning = nextWord.meaning,
            sentences_json = sentencesJson,
            sent_date = tomorrow,
            is_read = 0,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private fun pickWordFromAssets(
        context: Context,
        sentWords: Set<String>,
        userLevel: String,
    ): com.speakmind.app.feature.vocabulary.domain.model.VocabWord? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val vocabJson = context.assets.open(VOCAB_ASSET_PATH).bufferedReader().readText()
            val vocab = json.decodeFromString<VocabularyData>(vocabJson)

            val levelsToTry = buildList {
                add(userLevel)
                val idx = LEVEL_ORDER.indexOf(userLevel)
                if (idx >= 0 && idx + 1 < LEVEL_ORDER.size) add(LEVEL_ORDER[idx + 1])
            }

            for (level in levelsToTry) {
                val unsent = vocab.levels.find { it.level == level }
                    ?.words?.filter { it.word !in sentWords }
                if (!unsent.isNullOrEmpty()) return unsent.random()
            }

            for (level in LEVEL_ORDER) {
                if (level in levelsToTry) continue
                val unsent = vocab.levels.find { it.level == level }
                    ?.words?.filter { it.word !in sentWords }
                if (!unsent.isNullOrEmpty()) return unsent.random()
            }
            null
        } catch (e: Exception) {
            Napier.e { "Failed to pick word from assets: ${e.message}" }
            null
        }
    }

    private fun showNotification(context: Context, word: String, meaning: String) {
        val openIntent = Intent(context, Class.forName("com.speakmind.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORD, word)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Word of the Day: $word")
            .setContentText(meaning)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Word",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily vocabulary word notification"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
