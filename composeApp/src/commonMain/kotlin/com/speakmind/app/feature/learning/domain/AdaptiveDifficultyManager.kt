package com.speakmind.app.feature.learning.domain

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.ai.domain.DifficultyEvaluator
import com.speakmind.app.feature.ai.domain.model.CefrLevel
import com.speakmind.app.feature.chat.domain.model.ChatMessage

class AdaptiveDifficultyManager(private val database: SpeakyDatabase) {

    private var consecutiveEasyConversations = 0
    private var consecutiveHardConversations = 0

    fun evaluateAndAdjust(messages: List<ChatMessage>): LevelRecommendation {
        val assessment = DifficultyEvaluator.assessUserMessages(messages)

        database.speakMindQueries.insertDefaultProgress()
        val progress = database.speakMindQueries.selectProgress().executeAsOneOrNull()
        val currentLevel = CefrLevel.fromString(progress?.level ?: "A2")

        // Check if user is performing above or below their level
        return when {
            // User is doing well — low error rate at current level
            assessment.errorRate < 0.1f && assessment.estimatedLevel >= currentLevel -> {
                consecutiveEasyConversations++
                consecutiveHardConversations = 0

                if (consecutiveEasyConversations >= 3 && currentLevel < CefrLevel.C1) {
                    val newLevel = getNextLevel(currentLevel)
                    database.speakMindQueries.updateLevel(newLevel.label)
                    consecutiveEasyConversations = 0
                    LevelRecommendation.LevelUp(newLevel)
                } else {
                    LevelRecommendation.Stay(currentLevel)
                }
            }
            // User is struggling — high error rate
            assessment.errorRate > 0.4f -> {
                consecutiveHardConversations++
                consecutiveEasyConversations = 0

                if (consecutiveHardConversations >= 2 && currentLevel > CefrLevel.A1) {
                    val newLevel = getPrevLevel(currentLevel)
                    database.speakMindQueries.updateLevel(newLevel.label)
                    consecutiveHardConversations = 0
                    LevelRecommendation.LevelDown(newLevel)
                } else {
                    LevelRecommendation.Stay(currentLevel)
                }
            }
            else -> {
                consecutiveEasyConversations = 0
                consecutiveHardConversations = 0
                LevelRecommendation.Stay(currentLevel)
            }
        }
    }

    private fun getNextLevel(level: CefrLevel): CefrLevel = when (level) {
        CefrLevel.A1 -> CefrLevel.A2
        CefrLevel.A2 -> CefrLevel.B1
        CefrLevel.B1 -> CefrLevel.B2
        CefrLevel.B2 -> CefrLevel.C1
        CefrLevel.C1 -> CefrLevel.C1
    }

    private fun getPrevLevel(level: CefrLevel): CefrLevel = when (level) {
        CefrLevel.A1 -> CefrLevel.A1
        CefrLevel.A2 -> CefrLevel.A1
        CefrLevel.B1 -> CefrLevel.A2
        CefrLevel.B2 -> CefrLevel.B1
        CefrLevel.C1 -> CefrLevel.B2
    }
}

sealed class LevelRecommendation {
    data class LevelUp(val newLevel: CefrLevel) : LevelRecommendation()
    data class LevelDown(val newLevel: CefrLevel) : LevelRecommendation()
    data class Stay(val level: CefrLevel) : LevelRecommendation()
}
