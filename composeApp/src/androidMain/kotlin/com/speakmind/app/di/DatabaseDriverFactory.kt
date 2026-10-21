package com.speakmind.app.di

import android.content.Context
import app.cash.sqldelight.db.QueryResult
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
        """CREATE TABLE IF NOT EXISTS channel_messages (
            id TEXT NOT NULL PRIMARY KEY,
            sender_id TEXT NOT NULL,
            sender_nickname TEXT NOT NULL DEFAULT '',
            sender_photo_url TEXT NOT NULL DEFAULT '',
            sender_gender TEXT NOT NULL DEFAULT '',
            text_content TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            is_synced INTEGER NOT NULL DEFAULT 0
        )""",
        """CREATE TABLE IF NOT EXISTS vocab_groups (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS vocab_group_words (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            group_id INTEGER NOT NULL,
            word TEXT NOT NULL,
            meaning TEXT NOT NULL DEFAULT '',
            examples_json TEXT NOT NULL DEFAULT '[]',
            phonetic TEXT NOT NULL DEFAULT '',
            part_of_speech TEXT NOT NULL DEFAULT '',
            added_at INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS room_message_cache (
            id TEXT NOT NULL PRIMARY KEY,
            room_id TEXT NOT NULL,
            sender_id TEXT NOT NULL,
            sender_nickname TEXT NOT NULL DEFAULT '',
            sender_gender TEXT NOT NULL DEFAULT '',
            sender_photo_url TEXT,
            kind TEXT NOT NULL,
            body TEXT NOT NULL,
            voice_path TEXT,
            voice_duration_ms INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            created_at_raw TEXT NOT NULL,
            edited INTEGER NOT NULL DEFAULT 0
        )""",
        "CREATE INDEX IF NOT EXISTS idx_room_message_cache_room ON room_message_cache (room_id, created_at)",
        """CREATE TABLE IF NOT EXISTS course_progress (
            lesson_id TEXT NOT NULL PRIMARY KEY,
            score INTEGER NOT NULL,
            total INTEGER NOT NULL,
            completed_at INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS course_cache (
            cache_key TEXT NOT NULL PRIMARY KEY,
            json TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        )""",
        "ALTER TABLE room_message_cache ADD COLUMN reply_json TEXT",
        "ALTER TABLE room_message_cache ADD COLUMN reactions_json TEXT",
        "ALTER TABLE room_message_cache ADD COLUMN voice_waveform TEXT",
    ).forEach { sql ->
        // Upgrade steps run every launch; skip columns that already exist so SQLite doesn't log errors.
        val addColumn = ADD_COLUMN.find(sql)
        if (addColumn != null && columnExists(driver, addColumn.groupValues[1], addColumn.groupValues[2])) return@forEach
        try { driver.execute(null, sql.trimIndent(), 0) } catch (_: Exception) {}
    }
    return driver
}

private val ADD_COLUMN = Regex("""ALTER TABLE (\w+) ADD COLUMN (\w+)""", RegexOption.IGNORE_CASE)

private fun columnExists(driver: SqlDriver, table: String, column: String): Boolean = try {
    driver.executeQuery(
        identifier = null,
        sql = "PRAGMA table_info($table)",
        mapper = { cursor ->
            var found = false
            while (cursor.next().value) {
                if (cursor.getString(1).equals(column, ignoreCase = true)) found = true
            }
            QueryResult.Value(found)
        },
        parameters = 0,
    ).value
} catch (_: Exception) {
    false
}
