package com.speakmind.app.feature.ai.domain

import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.home.domain.model.Scenario

object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are an English language tutor named "Sage".
You are warm, encouraging, and patient.
Adapt vocabulary and sentence complexity to user level: {user_level}.
Keep responses conversational — 2-4 sentences unless asked for more.
If the user makes a grammar or vocabulary mistake, gently correct it
at the end of your reply with a one-line explanation prefixed with [CORRECTION:].
Always end your turn with a follow-up question to keep conversation going."""

    fun buildConversationPrompt(
        scenario: Scenario?,
        history: List<ChatMessage>,
        userLevel: String,
        mistakeTags: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()

        // System prompt
        var system = SYSTEM_PROMPT.replace("{user_level}", userLevel)
        if (scenario != null) {
            system += "\nCurrent scenario: ${scenario.title} — ${scenario.learningGoal}"
            system += "\nSuggested vocabulary: ${scenario.suggestedVocab.joinToString(", ")}"
        }
        if (mistakeTags.isNotEmpty()) {
            system += "\nUser's known weak points: ${mistakeTags.joinToString(", ")}"
        }

        sb.appendLine("<|system|>")
        sb.appendLine(system)
        sb.appendLine("<|end|>")

        // Conversation history (keep last 10 turns to fit context)
        val recentHistory = history.takeLast(10)
        for (message in recentHistory) {
            when (message.role) {
                MessageRole.USER -> {
                    sb.appendLine("<|user|>")
                    sb.appendLine(message.content)
                    sb.appendLine("<|end|>")
                }
                MessageRole.ASSISTANT -> {
                    sb.appendLine("<|assistant|>")
                    sb.appendLine(message.content)
                    sb.appendLine("<|end|>")
                }
                MessageRole.SYSTEM -> { /* skip */ }
            }
        }

        sb.appendLine("<|assistant|>")
        return sb.toString()
    }

    fun buildFreeTalkPrompt(
        history: List<ChatMessage>,
        userLevel: String,
    ): String = buildConversationPrompt(
        scenario = null,
        history = history,
        userLevel = userLevel,
    )

    fun buildRescuePrompt(
        scenario: Scenario?,
        userLevel: String,
    ): String {
        val hint = scenario?.rescueHint ?: "Try starting with a simple sentence."
        return "The user seems stuck. Rephrase your question more simply. " +
               "Give them a hint: '$hint'. Offer two simple options they can choose from."
    }
}
