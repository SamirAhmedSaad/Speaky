package com.speakmind.app.feature.splash.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.SplashDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun NavGraphBuilder.splashScreen() {
    animatedComposable<SplashDestination> {
        koinViewModel<SplashViewModel>()
        SplashScreenContent()
    }
}

@Composable
private fun SplashScreenContent() {
    val colors = LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.backgroundDark,
                        colors.backgroundMid,
                        colors.backgroundDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animated particles background
        ParticleBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.sdp)
        ) {
            // Logo: Sound wave lines + cyan dot
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(150.sdp)
            ) {
                // Glow layer
                Canvas(modifier = Modifier.size((150 * glowScale).sdp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.neonCyan.copy(alpha = 0.2f * pulseAlpha),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }
                // Sound wave lines
                Canvas(modifier = Modifier.size(120.sdp)) {
                    val w = size.width
                    val h = size.height

                    // Wave 1
                    val wave1 = Path().apply {
                        moveTo(0f, h * 0.5f)
                        for (i in 0..100) {
                            val x = w * i / 100f
                            val y = h * 0.5f + h * 0.15f * sin(2.5f * Math.PI.toFloat() * i / 100f)
                            lineTo(x, y)
                        }
                    }
                    drawPath(wave1, colors.neonCyan.copy(alpha = 0.4f * pulseAlpha),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round))

                    // Wave 2
                    val wave2 = Path().apply {
                        moveTo(0f, h * 0.56f)
                        for (i in 0..100) {
                            val x = w * i / 100f
                            val y = h * 0.56f + h * 0.12f * sin(2.8f * Math.PI.toFloat() * i / 100f + 1f)
                            lineTo(x, y)
                        }
                    }
                    drawPath(wave2, colors.neonCyanDark.copy(alpha = 0.3f * pulseAlpha),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.04f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round))

                    // Cyan dot accent
                    drawCircle(
                        color = colors.neonCyan,
                        radius = w * 0.035f,
                        center = Offset(w * 0.82f, h * 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.sdp))

            Text(
                text = "Speaky",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.ssp
                )
            )

            Spacer(modifier = Modifier.height(8.sdp))

            Text(
                text = "Your AI English Tutor",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.neonCyan.copy(alpha = 0.8f)
                )
            )

        }

    }
}

@Composable
private fun ParticleBackground() {
    val colors = LocalSpeakMindColors.current
    val particles = remember {
        List(30) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = Random.nextFloat() * 3f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.1f,
                speed = Random.nextFloat() * 0.0005f + 0.0001f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val offsetX = cos(time * particle.speed * 10) * 50f
            val offsetY = sin(time * particle.speed * 10) * 50f
            drawCircle(
                color = colors.neonCyan.copy(alpha = particle.alpha),
                radius = particle.radius.dp.toPx(),
                center = Offset(
                    x = particle.x * size.width + offsetX,
                    y = particle.y * size.height + offsetY
                )
            )
        }
    }
}

private fun buildCrescentPath(
    cx: Float, cy: Float,
    outerRx: Float, innerRx: Float, ry: Float,
    goRight: Boolean
): Path {
    return Path().apply {
        val outerRect = Rect(cx - outerRx, cy - ry, cx + outerRx, cy + ry)
        val innerRect = Rect(cx - innerRx, cy - ry, cx + innerRx, cy + ry)
        if (goRight) {
            // Top-right crescent: arcs bulge to the right
            arcTo(outerRect, 270f, 180f, false)
            arcTo(innerRect, 90f, -180f, false)
        } else {
            // Bottom-left crescent: arcs bulge to the left
            arcTo(outerRect, 270f, -180f, false)
            arcTo(innerRect, 90f, 180f, false)
        }
        close()
    }
}

private fun DrawScope.drawSparkle(center: Offset, sparkleSize: Float, color: Color, alpha: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - sparkleSize)
        lineTo(center.x + sparkleSize * 0.28f, center.y - sparkleSize * 0.28f)
        lineTo(center.x + sparkleSize, center.y)
        lineTo(center.x + sparkleSize * 0.28f, center.y + sparkleSize * 0.28f)
        lineTo(center.x, center.y + sparkleSize)
        lineTo(center.x - sparkleSize * 0.28f, center.y + sparkleSize * 0.28f)
        lineTo(center.x - sparkleSize, center.y)
        lineTo(center.x - sparkleSize * 0.28f, center.y - sparkleSize * 0.28f)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}

private data class Particle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val speed: Float,
)
