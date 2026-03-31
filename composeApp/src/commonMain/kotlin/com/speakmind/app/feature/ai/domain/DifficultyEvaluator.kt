package com.speakmind.app.feature.ai.domain

import com.speakmind.app.feature.ai.domain.model.CefrLevel
import com.speakmind.app.feature.ai.domain.model.DifficultyAssessment
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole

object DifficultyEvaluator {

    // Common simple words (A1-A2 level)
    private val simpleWords = setOf(
        "i", "you", "he", "she", "it", "we", "they", "is", "am", "are", "was", "were",
        "have", "has", "do", "does", "can", "will", "the", "a", "an", "and", "or", "but",
        "in", "on", "at", "to", "for", "of", "with", "from", "this", "that", "my", "your",
        "yes", "no", "not", "good", "bad", "like", "want", "need", "know", "think", "go",
        "come", "see", "make", "give", "take", "get", "put", "say", "tell", "ask", "work",
        "help", "try", "use", "find", "very", "much", "many", "some", "all", "one", "two",
    )

    fun assessUserMessages(messages: List<ChatMessage>): DifficultyAssessment {
        val userMessages = messages.filter { it.role == MessageRole.USER }
        if (userMessages.isEmpty()) {
            return DifficultyAssessment(CefrLevel.A2, 0f, 0f, 0f)
        }

        val allWords = userMessages.flatMap { it.content.lowercase().split("\\s+".toRegex()) }
        val avgLength = userMessages.map {
            it.content.split("\\s+".toRegex()).size
        }.average().toFloat()

        // Vocabulary complexity: ratio of non-simple words
        val complexWords = allWords.count { it !in simpleWords }
        val vocabComplexity = if (allWords.isNotEmpty()) {
            complexWords.toFloat() / allWords.size
        } else 0f

        // Error rate from corrections in AI responses
        val corrections = messages.filter { it.role == MessageRole.ASSISTANT }
            .sumOf { it.corrections.size }
        val errorRate = if (userMessages.isNotEmpty()) {
            corrections.toFloat() / userMessages.size
        } else 0f

        val level = when {
            avgLength < 4 && vocabComplexity < 0.2f -> CefrLevel.A1
            avgLength < 7 && vocabComplexity < 0.35f -> CefrLevel.A2
            avgLength < 12 && vocabComplexity < 0.5f -> CefrLevel.B1
            avgLength < 18 && vocabComplexity < 0.65f -> CefrLevel.B2
            else -> CefrLevel.C1
        }

        return DifficultyAssessment(
            estimatedLevel = level,
            errorRate = errorRate,
            avgSentenceLength = avgLength,
            vocabularyComplexity = vocabComplexity,
        )
    }
}
