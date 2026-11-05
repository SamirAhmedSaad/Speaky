package com.speakmind.app.feature.community.ui.components

/**
 * True when a loaded profile picture is Google's default "letter on a flat colour" image
 * (accounts without a real photo); those get a fun avatar instead.
 */
expect fun isPlainLetterImage(image: coil3.Image): Boolean

/** Results per photo URL, so a person's avatar is decided once per app run. */
internal object LetterImageCache {
    val results = mutableMapOf<String, Boolean>()
}
