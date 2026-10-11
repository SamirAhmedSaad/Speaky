package com.speakmind.app.feature.voice.platform

/** Microphone permission check for voice messages. */
expect object MicAccess {
    fun isGranted(): Boolean
    /** Shows the system permission dialog if needed. */
    fun request()
}
