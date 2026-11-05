package com.speakmind.app.feature.community.ui.components

import coil3.toBitmap
import kotlin.math.abs

actual fun isPlainLetterImage(image: coil3.Image): Boolean = try {
    // Hardware bitmaps can't be read pixel by pixel; a small software copy can.
    val loaded = image.toBitmap()
    val bitmap = if (loaded.config == android.graphics.Bitmap.Config.HARDWARE) loaded.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else loaded
    // Sample a grid; a letter avatar is one background colour plus a little white letter.
    val n = 20
    val w = bitmap.width
    val h = bitmap.height
    val bg = bitmap.getPixel(w / 10, h / 2)
    var same = 0
    var other = 0
    for (i in 0 until n) for (j in 0 until n) {
        val p = bitmap.getPixel((i + 0.5f).times(w / n.toFloat()).toInt().coerceIn(0, w - 1), (j + 0.5f).times(h / n.toFloat()).toInt().coerceIn(0, h - 1))
        val near = abs(((p shr 16) and 0xFF) - ((bg shr 16) and 0xFF)) +
            abs(((p shr 8) and 0xFF) - ((bg shr 8) and 0xFF)) +
            abs((p and 0xFF) - (bg and 0xFF)) < 30
        val white = ((p shr 16) and 0xFF) > 225 && ((p shr 8) and 0xFF) > 225 && (p and 0xFF) > 225
        if (near) same++ else if (!white) other++
    }
    same > n * n * 0.6 && other < n * n * 0.06
} catch (_: Throwable) {
    false
}
