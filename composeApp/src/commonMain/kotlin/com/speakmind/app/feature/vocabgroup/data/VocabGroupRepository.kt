package com.speakmind.app.feature.vocabgroup.data

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.vocabgroup.domain.model.VocabGroup
import com.speakmind.app.feature.vocabgroup.domain.model.VocabGroupWord
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VocabGroupRepository(
    private val database: SpeakyDatabase,
    private val json: Json,
) {

    suspend fun getGroups(): List<VocabGroup> {
        val rows = database.speakMindQueries.selectAllGroups().executeAsList()
        return rows.map { g ->
            val count = database.speakMindQueries.countGroupWords(g.id).executeAsOne()
            VocabGroup(
                id = g.id,
                name = g.name,
                wordCount = count.toInt(),
                createdAt = g.created_at,
            )
        }
    }

    suspend fun createGroup(name: String): Long {
        database.speakMindQueries.insertGroup(name, Clock.System.now().toEpochMilliseconds())
        return database.speakMindQueries.lastInsertRowId().executeAsOne()
    }

    suspend fun deleteGroup(id: Long) {
        database.speakMindQueries.deleteGroupWordsByGroup(id)
        database.speakMindQueries.deleteGroup(id)
    }

    suspend fun countAllGroups(): Long =
        database.speakMindQueries.countAllGroups().executeAsOne()

    suspend fun getWordsInGroup(groupId: Long): List<VocabGroupWord> =
        database.speakMindQueries.selectWordsInGroup(groupId).executeAsList().map { w ->
            VocabGroupWord(
                id = w.id,
                groupId = w.group_id,
                word = w.word,
                meaning = w.meaning,
                examples = runCatching { json.decodeFromString<List<String>>(w.examples_json) }.getOrDefault(emptyList()),
                phonetic = w.phonetic,
                partOfSpeech = w.part_of_speech,
                addedAt = w.added_at,
            )
        }

    suspend fun addWord(groupId: Long, word: String): Long {
        database.speakMindQueries.insertGroupWord(
            group_id = groupId,
            word = word,
            meaning = "",
            examples_json = "[]",
            phonetic = "",
            part_of_speech = "",
            added_at = Clock.System.now().toEpochMilliseconds(),
        )
        return database.speakMindQueries.lastInsertRowId().executeAsOne()
    }

    suspend fun updateWord(
        id: Long,
        word: String,
        meaning: String,
        examples: List<String>,
        phonetic: String,
        partOfSpeech: String,
    ) {
        database.speakMindQueries.updateGroupWord(
            word = word,
            meaning = meaning,
            examples_json = json.encodeToString(examples),
            phonetic = phonetic,
            part_of_speech = partOfSpeech,
            id = id,
        )
    }

    suspend fun deleteWord(id: Long) {
        database.speakMindQueries.deleteGroupWord(id)
    }
}
