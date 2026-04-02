package com.speakmind.app.feature.home.data

import com.speakmind.app.db.SpeakyDatabase

class DailyTopicCache(private val database: SpeakyDatabase) {

    fun getCachedTopics(date: String, userLevel: String): List<String>? {
        val ids = database.speakMindQueries.selectDailyTopics(date, userLevel).executeAsList()
        return ids.ifEmpty { null }
    }

    fun cacheTopics(date: String, userLevel: String, scenarioIds: List<String>) {
        scenarioIds.forEachIndexed { index, id ->
            database.speakMindQueries.insertDailyTopic(
                date = date,
                user_level = userLevel,
                scenario_id = id,
                position = index.toLong(),
            )
        }
    }

    fun cleanup(beforeDate: String) {
        database.speakMindQueries.deleteDailyTopicsOlderThan(beforeDate)
    }
}
