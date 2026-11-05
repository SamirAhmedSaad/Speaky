package com.speakmind.app.feature.community.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Chat-only strokes from the design that SpeakyIcons doesn't have. */
object ChatIcons {
    val WifiOff by lazy { stroke(24f, 24f, "M2 8.8a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 20h.01M3 3l18 18") }
    /** Double tick for "Seen" (28×16 viewport, as in the design). */
    val Seen by lazy { stroke(28f, 16f, "M2 9l4 4 9-10M12 13l1 0 9-10", width = 2.4f) }
    val Moon by lazy { stroke(24f, 24f, "M21 13A9 9 0 1 1 11 3a7 7 0 0 0 10 10z") }
    val Clock by lazy { stroke(24f, 24f, "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2") }
    val Alert by lazy { stroke(24f, 24f, "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 8v5M12 16h.01") }
    val Keyboard by lazy { stroke(24f, 24f, "M4 6h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1zM7 10h.01M11 10h.01M15 10h.01M8 14h8") }
    val ArrowDown by lazy { stroke(24f, 24f, "M6 9l6 6 6-6", width = 2.4f) }
    val Pause by lazy {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes("M7 5h3v14H7zM14 5h3v14h-3z"), fill = SolidColor(Color.Black))
            .build()
    }
    val Flag by lazy { stroke(24f, 24f, "M5 21V4M5 4h11l-2 4 2 4H5") }
    val Block by lazy { stroke(24f, 24f, "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM5.6 5.6l12.8 12.8") }
    val Mail by lazy { stroke(24f, 24f, "M4 6h16v12H4zM4 7l8 6 8-6") }
}

private fun stroke(w: Float, h: Float, d: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(defaultWidth = w.dp, defaultHeight = h.dp, viewportWidth = w, viewportHeight = h)
        .addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
