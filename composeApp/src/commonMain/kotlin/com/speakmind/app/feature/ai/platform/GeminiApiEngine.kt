package com.speakmind.app.feature.ai.platform

import com.speakmind.app.feature.ai.domain.AiEngine
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.geminichat.data.ApiKeyStore
import com.speakmind.app.feature.geminichat.data.GeminiRepository
import com.speakmind.app.feature.home.domain.model.Scenario

class GeminiApiEngine(
    private val geminiRepository: GeminiRepository,
    private val apiKeyStore: ApiKeyStore,
) : AiEngine {

    override val engineType = "gemini_api"

    override suspend fun chat(
        messages: List<ChatMessage>,
        userLevel: String,
        scenario: Scenario?,
        isStructured: Boolean,
    ): String {
        val key = apiKeyStore.getKey() ?: error("No Gemini API key set")
        return geminiRepository.chat(apiKey = key, messages = messages, userLevel = userLevel, scenario = scenario, isStructured = isStructured)
    }
}
