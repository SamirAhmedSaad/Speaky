package com.speakmind.app.feature.dailyword.data

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.dailyword.domain.model.DailyWordData
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json

class DailyWordRepository(private val database: SpeakyDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getAllDailyWords(): List<DailyWordData> {
        return database.speakMindQueries.selectAllDailyWords().executeAsList().map { it.toDomain() }
    }

    fun getRecentWords(limit: Int = 30): List<DailyWordData> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        return database.speakMindQueries.selectRecentDailyWords(today).executeAsList().map { it.toDomain() }
    }

    fun getTodayWord(today: String): DailyWordData? {
        return database.speakMindQueries.selectTodayWord(today).executeAsOneOrNull()?.toDomain()
    }

    fun getWordById(id: Long): DailyWordData? {
        return database.speakMindQueries.selectDailyWordById(id).executeAsOneOrNull()?.toDomain()
    }

    fun getWordByWord(word: String): DailyWordData? {
        return database.speakMindQueries.selectDailyWordByWord(word).executeAsOneOrNull()?.toDomain()
    }

    fun getAllSentWords(): List<String> {
        return database.speakMindQueries.selectAllSentWords().executeAsList()
    }

    fun insertDailyWord(
        word: String,
        level: String,
        partOfSpeech: String,
        meaning: String,
        sentencesJson: String,
        sentDate: String,
        createdAt: Long,
    ) {
        database.speakMindQueries.insertDailyWord(
            word = word,
            level = level,
            part_of_speech = partOfSpeech,
            meaning = meaning,
            sentences_json = sentencesJson,
            sent_date = sentDate,
            is_read = 0,
            created_at = createdAt,
        )
    }

    fun markAsRead(id: Long) {
        database.speakMindQueries.markDailyWordRead(id)
    }

    fun getCount(): Long {
        return database.speakMindQueries.countDailyWords().executeAsOne()
    }

    private fun com.speakmind.app.db.Daily_words.toDomain(): DailyWordData {
        val sentences = try {
            json.decodeFromString<List<String>>(sentences_json)
        } catch (_: Exception) {
            emptyList()
        }
        return DailyWordData(
            id = id,
            word = word,
            level = level,
            partOfSpeech = part_of_speech,
            meaning = meaning,
            sentences = sentences,
            sentDate = sent_date,
            isRead = is_read != 0L,
        )
    }
}
