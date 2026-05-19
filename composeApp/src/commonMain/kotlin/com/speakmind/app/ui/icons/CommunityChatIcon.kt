package com.speakmind.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CommunityChatIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CommunityChat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Primary bubble (top-left, larger) — proper rounded corners via quadTo
        path(fill = SolidColor(Color.White)) {
            moveTo(1f, 5f)
            quadTo(1f, 2f, 4f, 2f)
            lineTo(14f, 2f)
            quadTo(17f, 2f, 17f, 5f)
            lineTo(17f, 12f)
            quadTo(17f, 15f, 14f, 15f)
            lineTo(7f, 15f)
            lineTo(3f, 19f)
            lineTo(3f, 15f)
            quadTo(1f, 15f, 1f, 12f)
            close()
        }
        // Secondary bubble (bottom-right, smaller, semi-transparent)
        path(fill = SolidColor(Color.White), fillAlpha = 0.65f) {
            moveTo(9f, 10f)
            quadTo(9f, 8f, 11f, 8f)
            lineTo(20f, 8f)
            quadTo(22f, 8f, 22f, 10f)
            lineTo(22f, 16f)
            quadTo(22f, 18f, 20f, 18f)
            lineTo(17f, 18f)
            lineTo(17f, 21f)
            lineTo(14f, 18f)
            lineTo(11f, 18f)
            quadTo(9f, 18f, 9f, 16f)
            close()
        }
    }.build()
}
