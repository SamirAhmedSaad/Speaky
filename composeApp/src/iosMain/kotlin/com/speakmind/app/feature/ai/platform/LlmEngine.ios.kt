package com.speakmind.app.feature.ai.platform

import com.speakmind.app.feature.ai.domain.SmartResponseEngine
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.home.domain.model.Scenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * iOS LLM engine using llama.cpp via cinterop.
 * Currently uses SmartResponseEngine for context-aware responses.
 * TODO: Replace with actual llama.cpp cinterop bridge.
 */
actual class LlmEngine actual constructor() {

    private var loaded = false

    var conversationHistory: List<ChatMessage> = emptyList()
    var currentScenario: Scenario? = null
    var userLevel: String = "A2"

    actual suspend fun load(modelPath: String, contextSize: Int) {
        loaded = true
    }

    actual fun generate(prompt: String, maxTokens: Int, temperature: Float): Flow<String> = flow {
        val userMessage = extractLastUserMessage(prompt)

        val response = SmartResponseEngine.generateResponse(
            userMessage = userMessage,
            conversationHistory = conversationHistory,
            scenario = currentScenario,
            userLevel = userLevel,
        )

        val words = response.split(" ")
        for (word in words) {
            emit("$word ")
            delay(40)
        }
    }

    actual fun isLoaded(): Boolean = loaded

    actual fun unload() {
        loaded = false
    }

    private fun extractLastUserMessage(prompt: String): String {
        val lines = prompt.split("\n")
        var lastUserMsg = ""
        var inUserBlock = false
        for (line in lines) {
            if (line.trim() == "<|user|>") {
                inUserBlock = true
                lastUserMsg = ""
            } else if (line.trim() == "<|end|>" && inUserBlock) {
                inUserBlock = false
            } else if (inUserBlock) {
                lastUserMsg += line + " "
            }
        }
        return lastUserMsg.trim().ifEmpty {
            prompt.substringAfterLast("<|user|>", "")
                .substringBefore("<|end|>", "")
                .trim()
        }
    }
}
