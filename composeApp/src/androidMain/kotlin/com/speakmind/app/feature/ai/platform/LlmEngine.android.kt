package com.speakmind.app.feature.ai.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The on-device AI chat is turned off (Features.AI_CHAT), so the llama.cpp engine is no longer shipped
 * (saves about 17 MB). Nothing can load a model; the feature's screens are unreachable.
 */
actual class LlmEngine actual constructor() {
    actual suspend fun load(modelPath: String, contextSize: Int) {
        error("On-device AI is not available in this version")
    }

    actual fun generate(prompt: String, maxTokens: Int, temperature: Float): Flow<String> = emptyFlow()

    actual fun isLoaded(): Boolean = false

    actual fun unload() = Unit
}
