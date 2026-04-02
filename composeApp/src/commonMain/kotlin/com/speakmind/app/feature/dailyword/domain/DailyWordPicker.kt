package com.speakmind.app.feature.dailyword.domain

import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.vocabulary.data.VocabularyRepository
import com.speakmind.app.feature.vocabulary.domain.model.VocabWord

class DailyWordPicker(
    private val dailyWordRepository: DailyWordRepository,
    private val vocabularyRepository: VocabularyRepository,
) {
    private val levelOrder = listOf("A1", "A2", "B1", "B2", "C1")

    suspend fun pickWord(userLevel: String): VocabWord? {
        val sentWords = dailyWordRepository.getAllSentWords().toSet()
        val vocab = vocabularyRepository.loadVocabulary()

        // Try user's level first, then one level above
        val levelsToTry = buildList {
            add(userLevel)
            val idx = levelOrder.indexOf(userLevel)
            if (idx >= 0 && idx + 1 < levelOrder.size) {
                add(levelOrder[idx + 1])
            }
        }

        for (level in levelsToTry) {
            val vocabLevel = vocab.levels.find { it.level == level } ?: continue
            val unsent = vocabLevel.words.filter { it.word !in sentWords }
            if (unsent.isNotEmpty()) return unsent.random()
        }

        // Fallback: any level with unsent words
        for (level in levelOrder) {
            if (level in levelsToTry) continue
            val vocabLevel = vocab.levels.find { it.level == level } ?: continue
            val unsent = vocabLevel.words.filter { it.word !in sentWords }
            if (unsent.isNotEmpty()) return unsent.random()
        }

        return null
    }

    suspend fun pickWordLevel(userLevel: String): String? {
        val sentWords = dailyWordRepository.getAllSentWords().toSet()
        val vocab = vocabularyRepository.loadVocabulary()

        val levelsToTry = buildList {
            add(userLevel)
            val idx = levelOrder.indexOf(userLevel)
            if (idx >= 0 && idx + 1 < levelOrder.size) {
                add(levelOrder[idx + 1])
            }
        }

        for (level in levelsToTry) {
            val vocabLevel = vocab.levels.find { it.level == level } ?: continue
            val unsent = vocabLevel.words.filter { it.word !in sentWords }
            if (unsent.isNotEmpty()) return level
        }

        for (level in levelOrder) {
            if (level in levelsToTry) continue
            val vocabLevel = vocab.levels.find { it.level == level } ?: continue
            val unsent = vocabLevel.words.filter { it.word !in sentWords }
            if (unsent.isNotEmpty()) return level
        }

        return null
    }
}
