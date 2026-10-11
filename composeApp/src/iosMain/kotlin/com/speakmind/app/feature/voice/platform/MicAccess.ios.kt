package com.speakmind.app.feature.voice.platform

actual object MicAccess {
    actual fun isGranted(): Boolean = false
    actual fun request() {}
}
