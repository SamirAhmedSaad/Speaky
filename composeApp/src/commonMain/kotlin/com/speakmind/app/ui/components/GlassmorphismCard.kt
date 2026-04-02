package com.speakmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import network.chaintech.sdpcomposemultiplatform.sdp
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.SpeakMindColors

@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.sdp,
    borderAlpha: Float = 0.15f,
    backgroundAlpha: Float = 0.6f,
    contentPadding: Dp = 16.sdp,
    borderColor: Color = SpeakMindColors.neonCyan,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.surfaceVariant.copy(alpha = backgroundAlpha),
                        colors.surface.copy(alpha = backgroundAlpha * 0.7f),
                    )
                )
            )
            .border(
                width = 1.sdp,
                color = borderColor.copy(alpha = borderAlpha),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun GlowingCard(
    modifier: Modifier = Modifier,
    glowColor: Color = SpeakMindColors.neonCyan,
    cornerRadius: Dp = 20.sdp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.08f),
                        colors.surfaceVariant.copy(alpha = 0.7f),
                        colors.surface.copy(alpha = 0.5f),
                    )
                )
            )
            .border(
                width = 1.5.sdp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.4f),
                        glowColor.copy(alpha = 0.1f),
                    )
                ),
                shape = shape,
            )
            .padding(16.sdp),
        content = content,
    )
}
