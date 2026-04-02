package com.speakmind.app.feature.ai.domain

import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.home.domain.model.Scenario

interface AiEngine {
    val engineType: String
    suspend fun chat(
        messages: List<ChatMessage>,
        userLevel: String,
        scenario: Scenario? = null,
    ): String
}
