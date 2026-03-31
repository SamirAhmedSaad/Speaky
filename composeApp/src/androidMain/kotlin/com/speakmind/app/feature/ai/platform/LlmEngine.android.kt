package com.speakmind.app.feature.ai.platform

import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.flow.Flow

/**
 * Android LLM engine using llama.cpp via JNI.
 * Uses the official llama-android library for on-device inference.
 */
actual class LlmEngine actual constructor() {

    private val llama = LLamaAndroid.instance()

    actual suspend fun load(modelPath: String, contextSize: Int) {
        llama.load(modelPath)
    }

    actual fun generate(prompt: String, maxTokens: Int, temperature: Float): Flow<String> {
        return llama.send(prompt, formatChat = false)
    }

    actual fun isLoaded(): Boolean {
        // LLamaAndroid manages its own state internally
        return true
    }

    actual fun unload() {
        // Will be called when done
    }
}
