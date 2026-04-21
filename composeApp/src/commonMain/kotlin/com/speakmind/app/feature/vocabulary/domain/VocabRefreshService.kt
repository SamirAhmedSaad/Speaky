package com.speakmind.app.feature.vocabulary.domain

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.vocabulary.data.VocabularyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class VocabRefreshService(
    private val database: SpeakyDatabase,
    private val vocabularyRepository: VocabularyRepository,
) {
    private val json = Json
    private val listSerializer = ListSerializer(String.serializer())

    suspend fun refreshIfUpdated(currentVersionCode: Int) = withContext(Dispatchers.IO) {
        val storedVersion = database.speakMindQueries
            .selectVocabVersion()
            .executeAsOneOrNull()
            ?.toInt() ?: 0

        if (storedVersion == currentVersionCode) return@withContext

        val dailyWords = database.speakMindQueries.selectAllDailyWords().executeAsList()
        val vocabFlashcards = database.speakMindQueries.selectAllFlashcards().executeAsList()
            .filter { it.context?.startsWith("Word Builder:") == true }

        // Nothing saved yet — just stamp the version and skip heavy JSON load
        if (dailyWords.isEmpty() && vocabFlashcards.isEmpty()) {
            database.speakMindQueries.updateVocabVersion(currentVersionCode.toLong())
            return@withContext
        }

        val wordMap = vocabularyRepository.loadVocabulary()
            .levels.flatMap { it.words }
            .associate { it.word to it }

        dailyWords.forEach { row ->
            val fresh = wordMap[row.word] ?: return@forEach
            database.speakMindQueries.updateDailyWordVocab(
                meaning = fresh.meaning,
                sentences_json = json.encodeToString(listSerializer, fresh.sentences),
                word = row.word,
            )
        }

        vocabFlashcards.map { it.word }.distinct().forEach { word ->
            val fresh = wordMap[word] ?: return@forEach
            database.speakMindQueries.updateFlashcardMeaning(
                grammar_note = fresh.meaning,
                word = word,
            )
        }

        database.speakMindQueries.updateVocabVersion(currentVersionCode.toLong())
    }
}
