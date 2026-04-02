package com.speakmind.app.feature.ai.platform

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PreloadState { IDLE, LOADING, LOADED, NO_MODEL, ERROR }

class ModelPreloader(
    private val modelDownloader: ModelDownloader,
) {
    private val llmEngine = LlmEngine()

    private val _state = MutableStateFlow(PreloadState.IDLE)
    val state: StateFlow<PreloadState> = _state.asStateFlow()

    private val _statusText = MutableStateFlow("Preparing SpeakMind...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    val engine: LlmEngine get() = llmEngine

    val isLoaded: Boolean get() = _state.value == PreloadState.LOADED

    suspend fun preload() {
        if (_state.value == PreloadState.LOADED || _state.value == PreloadState.LOADING) return

        _state.value = PreloadState.LOADING
        _statusText.value = "Loading AI model..."

        try {
            val modelPath = findModelPath()
            if (modelPath != null) {
                Napier.d { "Preloading model from: $modelPath" }
                llmEngine.load(modelPath)
                _state.value = PreloadState.LOADED
                _statusText.value = "Ready to learn!"
                Napier.d { "Model preloaded successfully" }
            } else {
                Napier.w { "No model file found" }
                _state.value = PreloadState.NO_MODEL
                _statusText.value = "No AI model found"
            }
        } catch (e: Exception) {
            Napier.e(e) { "Failed to preload model: ${e.message}" }
            _state.value = PreloadState.ERROR
            _statusText.value = "Failed to load AI model"
        }
    }

    private fun findModelPath(): String? {
        modelDownloader.getModelPath()?.let { return it }
        return findModelFile()
    }

    fun unload() {
        llmEngine.unload()
        _state.value = PreloadState.IDLE
    }
}
