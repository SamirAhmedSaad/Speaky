package com.speakmind.app.feature.ai.platform

import com.speakmind.app.feature.ai.domain.AiEngine
import com.speakmind.app.feature.ai.domain.PromptBuilder
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.home.domain.model.Scenario
import kotlinx.coroutines.flow.toList

class LocalLlmAiEngine(private val llmEngine: LlmEngine) : AiEngine {

    override val engineType = "local"

    override suspend fun chat(
        messages: List<ChatMessage>,
        userLevel: String,
        scenario: Scenario?,
    ): String {
        val prompt = PromptBuilder.buildConversationPrompt(
            scenario = scenario,
            history = messages,
            userLevel = userLevel,
        )
        val stopSequences = listOf(
            "<|eot_id|>", "<|start_header_id|>", "<|end_header_id|>",
            "<|begin_of_text|>", "</s>"
        )
        val sb = StringBuilder()
        var shouldStop = false

        llmEngine.generate(prompt).collect { token ->
            if (shouldStop) return@collect
            sb.append(token)
            val current = sb.toString()
            for (stop in stopSequences) {
                if (current.contains(stop)) {
                    val idx = current.indexOf(stop)
                    sb.clear()
                    sb.append(current.substring(0, idx))
                    shouldStop = true
                    break
                }
            }
        }

        var result = sb.toString().trim()
        for (stop in stopSequences) {
            result = result.replace(stop, "")
        }
        return result.trim()
    }
}
