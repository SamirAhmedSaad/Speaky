package com.speakmind.app.feature.community.moderation

actual object PhotoSafety {
    actual suspend fun check(imageBytes: ByteArray): PhotoScores? = null
}
