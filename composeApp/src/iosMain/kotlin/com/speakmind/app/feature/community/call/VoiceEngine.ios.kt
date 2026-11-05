package com.speakmind.app.feature.community.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Voice calls aren't on iOS yet: joining fails, so the app shows "Couldn't connect". */
private class UnsupportedVoiceEngine : VoiceEngine {
    override val speaking: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val connected: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun join(token: VoiceToken, userId: String): Unit = error("Voice calls aren't available on iOS yet")
    override fun setMuted(muted: Boolean) = Unit
    override fun setSpeaker(on: Boolean) = Unit
    override fun leave() = Unit
}

actual fun createVoiceEngine(provider: String): VoiceEngine = UnsupportedVoiceEngine()

actual object CallKeepAlive {
    actual fun start(title: String, text: String, muted: Boolean) = Unit
    actual fun update(title: String, text: String, muted: Boolean) = Unit
    actual fun stop() = Unit
}
