package com.speakmind.app.feature.ai.domain

import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.home.domain.model.Scenario

/**
 * Builds prompts using the Llama 3.2 Instruct chat template.
 * Format:
 *   <|begin_of_text|><|start_header_id|>system<|end_header_id|>
 *   {system message}<|eot_id|>
 *   <|start_header_id|>user<|end_header_id|>
 *   {user message}<|eot_id|>
 *   <|start_header_id|>assistant<|end_header_id|>
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are an English language tutor named Sage. You are warm, encouraging, and patient. Keep responses conversational, 2-4 sentences. Always answer the user's question directly and helpfully. If the user makes a grammar mistake, gently correct it at the end. Always end with a follow-up question."""

    fun buildConversationPrompt(
        scenario: Scenario?,
        history: List<ChatMessage>,
        userLevel: String,
        mistakeTags: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()

        // System message
        var system = SYSTEM_PROMPT + " The user's English level is $userLevel."
        if (scenario != null) {
            system += " Current topic: ${scenario.title}."
            if (scenario.emotionalStakes.isNotBlank()) {
                system += " Article summary: ${scenario.emotionalStakes}"
            }
            if (scenario.learningGoal.isNotBlank()) {
                system += " Goal: ${scenario.learningGoal}."
            }
            system += " Stay on this topic. Use details from the article summary to ask relevant questions and guide the discussion."
        }
        if (mistakeTags.isNotEmpty()) {
            system += " User's weak points: ${mistakeTags.joinToString(", ")}."
        }

        sb.append("<|begin_of_text|>")
        sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
        sb.append(system)
        sb.append("<|eot_id|>")

        // Conversation history (keep last 8 turns to fit context)
        val recentHistory = history.takeLast(8)
        for (message in recentHistory) {
            when (message.role) {
                MessageRole.USER -> {
                    sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                    sb.append(message.content)
                    sb.append("<|eot_id|>")
                }
                MessageRole.ASSISTANT -> {
                    sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                    sb.append(message.content)
                    sb.append("<|eot_id|>")
                }
                MessageRole.SYSTEM -> { /* skip */ }
            }
        }

        // Prompt for assistant response
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
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
}
