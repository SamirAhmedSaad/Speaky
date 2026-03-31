package com.speakmind.app.feature.learning.domain

import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.home.domain.model.Scenario

enum class RescueLevel { NONE, REPHRASE, HINT, MULTIPLE_CHOICE }

data class RescueAction(
    val level: RescueLevel,
    val promptModifier: String,
)

object ConversationRescuer {

    fun detectStrugglingUser(messages: List<ChatMessage>): RescueLevel {
        val userMessages = messages.filter { it.role == MessageRole.USER }
        if (userMessages.size < 2) return RescueLevel.NONE

        val recent = userMessages.takeLast(3)

        // Check for short/empty responses
        val shortResponses = recent.count { it.content.trim().split("\\s+".toRegex()).size <= 2 }
        if (shortResponses >= 3) return RescueLevel.MULTIPLE_CHOICE

        // Check for help phrases
        val lastMsg = recent.lastOrNull()?.content?.lowercase() ?: ""
        if (lastMsg.contains("i don't know") || lastMsg.contains("i'm stuck") ||
            lastMsg.contains("help") || lastMsg.contains("i can't")) {
            return RescueLevel.HINT
        }

        // Check for consecutive short responses
        if (shortResponses >= 2) return RescueLevel.REPHRASE

        return RescueLevel.NONE
    }

    fun buildRescueAction(level: RescueLevel, scenario: Scenario?): RescueAction {
        val hint = scenario?.rescueHint ?: "Try starting with a simple sentence about yourself."

        return when (level) {
            RescueLevel.NONE -> RescueAction(level, "")
            RescueLevel.REPHRASE -> RescueAction(
                level,
                "The user seems unsure. Rephrase your question using simpler words and shorter sentences. " +
                "Stay encouraging."
            )
            RescueLevel.HINT -> RescueAction(
                level,
                "The user needs help. Give them a hint: '$hint'. " +
                "Show them how to start their answer. Be very encouraging and patient."
            )
            RescueLevel.MULTIPLE_CHOICE -> RescueAction(
                level,
                "The user is stuck. Offer them two simple options to choose from. " +
                "For example: 'You could say: A) \"$hint\" or B) something else entirely. " +
                "Which sounds better to you?' Keep it very simple and encouraging."
            )
        }
    }
}
