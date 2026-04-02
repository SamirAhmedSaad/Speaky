package com.speakmind.app.feature.ai.platform

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.ai.domain.AiEngine
import com.speakmind.app.feature.geminichat.data.ApiKeyStore
import com.speakmind.app.feature.geminichat.data.GeminiRepository

class AiEngineProvider(
    private val database: SpeakyDatabase,
    private val apiKeyStore: ApiKeyStore,
    private val geminiRepository: GeminiRepository,
    private val modelDownloader: ModelDownloader,
    private val modelPreloader: ModelPreloader,
) {
    fun hasGeminiKey(): Boolean = apiKeyStore.hasKey()
    fun hasLocalModel(): Boolean = modelDownloader.modelExists()
    fun clearGeminiKey() = apiKeyStore.clearKey()

    suspend fun getActiveEngine(): AiEngine? {
        val pref = database.speakMindQueries.selectProgress().executeAsOneOrNull()?.ai_engine ?: ""
        return when {
            pref == "gemini_api" && apiKeyStore.hasKey() ->
                GeminiApiEngine(geminiRepository, apiKeyStore)
            pref == "local" && modelDownloader.modelExists() -> {
                if (!modelPreloader.isLoaded) {
                    modelPreloader.preload()
                }
                if (modelPreloader.isLoaded) LocalLlmAiEngine(modelPreloader.engine)
                else null
            }
            else -> null
        }
    }
}
