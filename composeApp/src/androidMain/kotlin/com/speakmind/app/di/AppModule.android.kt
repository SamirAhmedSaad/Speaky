package com.speakmind.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.geminichat.data.ApiKeyStore
import com.speakmind.app.feature.ai.platform.AndroidModelDownloader
import com.speakmind.app.feature.ai.platform.ModelDownloader
import com.speakmind.app.feature.dailyword.platform.DailyWordNotificationScheduler
import com.speakmind.app.feature.voice.platform.MicPermissionRequester
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appModule: Module
    get() = module {
        single<SqlDriver> {
            val driver = AndroidSqliteDriver(
                SpeakyDatabase.Schema,
                androidContext(),
                "speakmind.db"
            )
            try {
                driver.execute(null, "ALTER TABLE progress ADD COLUMN user_name TEXT NOT NULL DEFAULT ''", 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, "ALTER TABLE progress ADD COLUMN ai_engine TEXT NOT NULL DEFAULT ''", 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, """
                    CREATE TABLE IF NOT EXISTS daily_topics (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        date TEXT NOT NULL,
                        user_level TEXT NOT NULL,
                        scenario_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        UNIQUE(date, scenario_id)
                    )
                """.trimIndent(), 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, """
                    CREATE TABLE IF NOT EXISTS conversations (
                        id TEXT NOT NULL PRIMARY KEY,
                        scenario_id TEXT,
                        messages_json TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        duration_seconds INTEGER NOT NULL DEFAULT 0,
                        user_level TEXT NOT NULL DEFAULT 'A2'
                    )
                """.trimIndent(), 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, """
                    CREATE TABLE IF NOT EXISTS rss_topics (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        level TEXT NOT NULL,
                        description TEXT NOT NULL,
                        ai_opening TEXT NOT NULL,
                        article_url TEXT NOT NULL,
                        image_url TEXT NOT NULL DEFAULT '',
                        fetched_date TEXT NOT NULL
                    )
                """.trimIndent(), 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, "ALTER TABLE progress ADD COLUMN dark_mode INTEGER NOT NULL DEFAULT -1", 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, """
                    CREATE TABLE IF NOT EXISTS daily_words (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        word TEXT NOT NULL,
                        level TEXT NOT NULL,
                        part_of_speech TEXT NOT NULL DEFAULT '',
                        meaning TEXT NOT NULL DEFAULT '',
                        sentences_json TEXT NOT NULL DEFAULT '[]',
                        sent_date TEXT NOT NULL,
                        is_read INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent(), 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, """
                    CREATE TABLE IF NOT EXISTS user_settings (
                        id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
                        notification_hour INTEGER NOT NULL DEFAULT 22,
                        notification_minute INTEGER NOT NULL DEFAULT 0,
                        notifications_enabled INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent(), 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, "ALTER TABLE progress ADD COLUMN tts_speed REAL NOT NULL DEFAULT 1.0", 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, "ALTER TABLE user_settings ADD COLUMN app_launch_count INTEGER NOT NULL DEFAULT 0", 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, "ALTER TABLE user_settings ADD COLUMN exact_alarm_dialog_count INTEGER NOT NULL DEFAULT 0", 0)
            } catch (_: Exception) {}
            try {
                driver.execute(null, "ALTER TABLE progress ADD COLUMN vocab_version INTEGER NOT NULL DEFAULT 0", 0)
            } catch (_: Exception) {}
            driver
        }
        single<ModelDownloader> { AndroidModelDownloader(androidContext()) }
        single { DailyWordNotificationScheduler(androidContext()) }
        single { ApiKeyStore(androidContext()) }
        single { MicPermissionRequester() }
    }
