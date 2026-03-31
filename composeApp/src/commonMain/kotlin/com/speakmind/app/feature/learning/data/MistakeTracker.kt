package com.speakmind.app.feature.learning.data

import com.speakmind.app.db.Mistakes
import com.speakmind.app.db.SpeakMindDatabase
import com.speakmind.app.feature.chat.domain.model.Correction
import kotlinx.datetime.Clock

class MistakeTracker(private val database: SpeakMindDatabase) {

    fun trackCorrection(correction: Correction) {
        val existing = database.speakMindQueries.findMistakeByWord(correction.original)
            .executeAsOneOrNull()

        val now = Clock.System.now().toEpochMilliseconds()

        if (existing != null) {
            database.speakMindQueries.updateMistakeCount(now, existing.id)
        } else {
            database.speakMindQueries.insertMistake(
                word = correction.original,
                error_type = correction.type,
                correction = correction.corrected,
                explanation = correction.explanation,
                count = 1,
                last_seen = now,
            )
        }
    }

    fun getTopMistakes(): List<Mistakes> {
        return database.speakMindQueries.selectTopMistakes().executeAsList()
    }

    fun getAllMistakes(): List<Mistakes> {
        return database.speakMindQueries.selectAllMistakes().executeAsList()
    }

    fun getMistakeTags(): List<String> {
        return getTopMistakes().map { "${it.error_type}: ${it.word}" }
    }
}
