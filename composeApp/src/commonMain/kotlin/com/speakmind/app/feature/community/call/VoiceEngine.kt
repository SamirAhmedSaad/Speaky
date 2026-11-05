package com.speakmind.app.feature.community.call

import kotlinx.coroutines.flow.StateFlow

/** Where and how to connect, from the voice-token function. */
data class VoiceToken(
    val provider: String,
    val token: String,
    val channel: String,
    /** Agora only. */
    val appId: String? = null,
    /** LiveKit only. */
    val url: String? = null,
)

/** One audio provider (Agora or LiveKit) behind the Hub call. */
interface VoiceEngine {
    /** User ids talking right now. */
    val speaking: StateFlow<Set<String>>

    /** False once the connection dropped for good. */
    val connected: StateFlow<Boolean>

    /** Connects and sends the microphone; throws when the provider can't connect. */
    suspend fun join(token: VoiceToken, userId: String)

    fun setMuted(muted: Boolean)

    fun setSpeaker(on: Boolean)

    fun leave()
}

const val PROVIDER_AGORA = "agora"
const val PROVIDER_LIVEKIT = "livekit"

expect fun createVoiceEngine(provider: String): VoiceEngine

/**
 * Keeps a call running with the app in the background or the screen locked: an ongoing
 * notification with Mute and Leave on Android.
 */
expect object CallKeepAlive {
    fun start(title: String, text: String, muted: Boolean)

    fun update(title: String, text: String, muted: Boolean)

    fun stop()
}
