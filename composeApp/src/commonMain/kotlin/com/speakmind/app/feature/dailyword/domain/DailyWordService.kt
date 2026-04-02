package com.speakmind.app.feature.dailyword.domain

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.dailyword.domain.model.DailyWordData
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class DailyWordService(
    private val dailyWordRepository: DailyWordRepository,
    private val dailyWordPicker: DailyWordPicker,
    private val database: SpeakyDatabase,
) {
    private val json = Json
    private val listSerializer = ListSerializer(String.serializer())

    suspend fun getOrCreateTodayWord(userLevel: String): DailyWordData? {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        dailyWordRepository.getTodayWord(today)?.let { return it }

        val word = dailyWordPicker.pickWord(userLevel) ?: return null
        val level = dailyWordPicker.pickWordLevel(userLevel) ?: userLevel
        val sentencesJson = json.encodeToString(listSerializer, word.sentences)

        dailyWordRepository.insertDailyWord(
            word = word.word,
            level = level,
            partOfSpeech = word.partOfSpeech,
            meaning = word.meaning,
            sentencesJson = sentencesJson,
            sentDate = today,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )

        return dailyWordRepository.getTodayWord(today)
    }

    suspend fun prepareNextDayWord(userLevel: String): DailyWordData? {
        val tomorrow = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(1, DateTimeUnit.DAY).toString()
        dailyWordRepository.getTodayWord(tomorrow)?.let { return it }

        val word = dailyWordPicker.pickWord(userLevel) ?: return null
        val level = dailyWordPicker.pickWordLevel(userLevel) ?: userLevel
        val sentencesJson = json.encodeToString(listSerializer, word.sentences)

        dailyWordRepository.insertDailyWord(
            word = word.word,
            level = level,
            partOfSpeech = word.partOfSpeech,
            meaning = word.meaning,
            sentencesJson = sentencesJson,
            sentDate = tomorrow,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )

        return dailyWordRepository.getTodayWord(tomorrow)
    }
}
