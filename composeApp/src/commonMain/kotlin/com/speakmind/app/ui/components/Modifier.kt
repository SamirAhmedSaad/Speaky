package com.speakmind.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = this.clickable(
    indication = null,
    interactionSource = null,
    onClick = onClick
)

fun Modifier.glowBorder(
    color: Color,
    cornerRadius: Dp = 16.dp,
    glowRadius: Dp = 8.dp,
    alpha: Float = 0.4f
): Modifier = this.drawBehind {
    drawRoundRect(
        color = color.copy(alpha = alpha),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        size = size
    )
}

fun Modifier.gradientBackground(brush: Brush): Modifier = this.drawBehind {
    drawRect(brush = brush)
}
