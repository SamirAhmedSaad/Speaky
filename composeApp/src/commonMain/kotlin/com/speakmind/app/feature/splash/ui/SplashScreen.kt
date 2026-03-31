package com.speakmind.app.feature.splash.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.SplashDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.SpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun NavGraphBuilder.splashScreen() {
    animatedComposable<SplashDestination> {
        val viewModel = koinViewModel<SplashViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        SplashScreenContent(uiState = uiState, onRetry = viewModel::retryDownload)
    }
}

@Composable
private fun SplashScreenContent(
    uiState: SplashUiState,
    onRetry: () -> Unit,
) {
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
                        SpeakMindColors.backgroundDark,
                        SpeakMindColors.backgroundMid,
                        SpeakMindColors.backgroundDark
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
            modifier = Modifier.padding(32.dp)
        ) {
            // Glowing logo circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Canvas(modifier = Modifier.size((140 * glowScale).dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SpeakMindColors.neonCyan.copy(alpha = 0.3f * pulseAlpha),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }
                Canvas(modifier = Modifier.size(100.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SpeakMindColors.neonCyan.copy(alpha = 0.8f),
                                SpeakMindColors.neonCyanDark.copy(alpha = 0.4f),
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }
                Text(
                    text = "S",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpeakMindColors.backgroundDark
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SpeakMind",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = SpeakMindColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your AI English Tutor",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = SpeakMindColors.neonCyan.copy(alpha = 0.8f)
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (uiState.isDownloading) {
                LinearProgressIndicator(
                    progress = { uiState.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(4.dp),
                    color = SpeakMindColors.neonCyan,
                    trackColor = SpeakMindColors.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (!uiState.isModelReady && !uiState.isError) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = SpeakMindColors.neonCyan,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SpeakMindColors.textSecondary
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ParticleBackground() {
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
                color = SpeakMindColors.neonCyan.copy(alpha = particle.alpha),
                radius = particle.radius.dp.toPx(),
                center = Offset(
                    x = particle.x * size.width + offsetX,
                    y = particle.y * size.height + offsetY
                )
            )
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val speed: Float,
)
