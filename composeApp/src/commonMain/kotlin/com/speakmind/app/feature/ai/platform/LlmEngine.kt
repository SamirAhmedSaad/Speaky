package com.speakmind.app.feature.ai.platform

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific LLM inference engine.
 * Android: JNI bridge to llama.cpp .so library
 * iOS: cinterop or Swift bridge to llama.cpp .a static library
 */
expect class LlmEngine() {
    suspend fun load(modelPath: String, contextSize: Int = 2048)
    fun generate(prompt: String, maxTokens: Int = 512, temperature: Float = 0.7f): Flow<String>
    fun isLoaded(): Boolean
    fun unload()
}
