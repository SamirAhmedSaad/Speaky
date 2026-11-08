package com.speakmind.app.feature.community.moderation

/** Scores (0..1) from the on-device photo model: drawings, hentai, neutral, porn, sexy. */
data class PhotoScores(val drawings: Float, val hentai: Float, val neutral: Float, val porn: Float, val sexy: Float) {
    /** Not modest: nudity, sexual, or revealing (swimwear, lingerie). */
    val immodest: Boolean get() = porn + hentai + sexy >= IMMODEST_SUM || sexy >= SEXY_MIN || porn >= PORN_MIN

    companion object {
        const val IMMODEST_SUM = 0.45f
        const val SEXY_MIN = 0.35f
        const val PORN_MIN = 0.25f
    }
}

/**
 * Checks a profile photo on the phone (no server, works offline). Null when it couldn't run
 * (unsupported platform, unreadable image): callers then allow the photo, so nothing is blocked.
 */
expect object PhotoSafety {
    suspend fun check(imageBytes: ByteArray): PhotoScores?
}
