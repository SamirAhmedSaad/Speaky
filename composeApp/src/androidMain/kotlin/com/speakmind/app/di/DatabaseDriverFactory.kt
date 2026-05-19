package com.speakmind.app.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.speakmind.app.db.SpeakyDatabase

fun createSpeakMindDriver(context: Context): SqlDriver {
    val driver = AndroidSqliteDriver(SpeakyDatabase.Schema, context, "speakmind.db")
    listOf(
        "ALTER TABLE progress ADD COLUMN user_name TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE progress ADD COLUMN ai_engine TEXT NOT NULL DEFAULT ''",
        """CREATE TABLE IF NOT EXISTS daily_topics (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            user_level TEXT NOT NULL,
            scenario_id TEXT NOT NULL,
            position INTEGER NOT NULL,
            UNIQUE(date, scenario_id)
        )""",
        """CREATE TABLE IF NOT EXISTS conversations (
            id TEXT NOT NULL PRIMARY KEY,
            scenario_id TEXT,
            messages_json TEXT NOT NULL,
            date INTEGER NOT NULL,
            duration_seconds INTEGER NOT NULL DEFAULT 0,
            user_level TEXT NOT NULL DEFAULT 'A2'
        )""",
        """CREATE TABLE IF NOT EXISTS rss_topics (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            category TEXT NOT NULL,
            level TEXT NOT NULL,
            description TEXT NOT NULL,
            ai_opening TEXT NOT NULL,
            article_url TEXT NOT NULL,
            image_url TEXT NOT NULL DEFAULT '',
            fetched_date TEXT NOT NULL
        )""",
        "ALTER TABLE progress ADD COLUMN dark_mode INTEGER NOT NULL DEFAULT -1",
        """CREATE TABLE IF NOT EXISTS daily_words (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            word TEXT NOT NULL,
            level TEXT NOT NULL,
            part_of_speech TEXT NOT NULL DEFAULT '',
            meaning TEXT NOT NULL DEFAULT '',
            sentences_json TEXT NOT NULL DEFAULT '[]',
            sent_date TEXT NOT NULL,
            is_read INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS user_settings (
            id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
            notification_hour INTEGER NOT NULL DEFAULT 22,
            notification_minute INTEGER NOT NULL DEFAULT 0,
            notifications_enabled INTEGER NOT NULL DEFAULT 1
        )""",
        "ALTER TABLE progress ADD COLUMN tts_speed REAL NOT NULL DEFAULT 1.0",
        "ALTER TABLE user_settings ADD COLUMN app_launch_count INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE user_settings ADD COLUMN exact_alarm_dialog_count INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE progress ADD COLUMN vocab_version INTEGER NOT NULL DEFAULT 0",
        """CREATE TABLE IF NOT EXISTS community_profile (
            id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
            firebase_uid TEXT NOT NULL DEFAULT '',
            nickname TEXT NOT NULL DEFAULT '',
            gender TEXT NOT NULL DEFAULT '',
            photo_url TEXT NOT NULL DEFAULT ''
        )""",
        "ALTER TABLE community_profile ADD COLUMN photo_url TEXT NOT NULL DEFAULT ''",
        """CREATE TABLE IF NOT EXISTS community_messages (
            id TEXT NOT NULL PRIMARY KEY,
            chat_id TEXT NOT NULL,
            sender_id TEXT NOT NULL,
            text_content TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            is_synced INTEGER NOT NULL DEFAULT 0
        )""",
        """CREATE TABLE IF NOT EXISTS community_unread (
            chat_id TEXT NOT NULL PRIMARY KEY,
            other_user_id TEXT NOT NULL DEFAULT '',
            unread_count INTEGER NOT NULL DEFAULT 0
        )""",
        """CREATE TABLE IF NOT EXISTS community_daily_quota (
            id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
            date TEXT NOT NULL DEFAULT '',
            messages_sent INTEGER NOT NULL DEFAULT 0
        )""",
    ).forEach { sql ->
        try { driver.execute(null, sql.trimIndent(), 0) } catch (_: Exception) {}
    }
    return driver
}
