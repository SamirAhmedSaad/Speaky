package com.speakmind.app.feature.download.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.ModelDownloadDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.SpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.modelDownloadScreen() {
    animatedComposable<ModelDownloadDestination> { backStackEntry ->
        val scenarioId = backStackEntry.arguments?.getString("scenarioId")
        val viewModel = koinViewModel<ModelDownloadViewModel> { parametersOf(scenarioId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        ModelDownloadContent(
            uiState = uiState,
            onStartDownload = viewModel::onStartDownload,
            onCancel = viewModel::onCancel,
            onRetry = viewModel::onRetry,
            onGoBack = viewModel::onGoBack,
        )
    }
}

@Composable
private fun ModelDownloadContent(
    uiState: DownloadUiState,
    onStartDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeakMindColors.backgroundGradient)
    ) {
        // Back button
        IconButton(
            onClick = onGoBack,
            modifier = Modifier
                .padding(top = 48.dp, start = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SpeakMindColors.textPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                uiState.isComplete -> DownloadComplete()
                uiState.isError -> DownloadError(onRetry = onRetry)
                uiState.hasStarted || uiState.isDownloading -> DownloadProgress(uiState = uiState, onCancel = onCancel)
                else -> DownloadPrompt(onStartDownload = onStartDownload)
            }
        }
    }
}

@Composable
private fun DownloadPrompt(onStartDownload: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Icon
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size((120 * iconScale).dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(SpeakMindColors.neonCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = SpeakMindColors.neonCyan,
                modifier = Modifier.size(48.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "AI Model Required",
        style = MaterialTheme.typography.headlineMedium.copy(
            color = SpeakMindColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "To have real conversations, SpeakMind needs to download an AI model. This is a one-time download.",
        style = MaterialTheme.typography.bodyLarge.copy(
            color = SpeakMindColors.textSecondary,
            lineHeight = 24.sp,
        ),
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SpeakMindColors.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            Icons.Default.Wifi,
            contentDescription = null,
            tint = SpeakMindColors.neonCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "WiFi only \u2022 Can resume if interrupted",
            style = MaterialTheme.typography.bodySmall.copy(
                color = SpeakMindColors.textMuted
            )
        )
    }

    Spacer(modifier = Modifier.height(40.dp))

    Button(
        onClick = onStartDownload,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SpeakMindColors.neonCyan,
            contentColor = SpeakMindColors.backgroundDark,
        )
    ) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Download AI Model",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun DownloadProgress(uiState: DownloadUiState, onCancel: () -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress / 100f,
        animationSpec = tween(300)
    )

    // Circular progress
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            // Background circle
            drawArc(
                color = SpeakMindColors.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(SpeakMindColors.neonCyan, SpeakMindColors.magenta)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${uiState.progress}%",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = SpeakMindColors.neonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = if (uiState.waitingForWifi) "Waiting for WiFi connection..."
               else "Downloading AI Model...",
        style = MaterialTheme.typography.titleMedium.copy(
            color = SpeakMindColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (uiState.totalMB > 0) {
        Text(
            text = "${uiState.downloadedMB} MB / ${uiState.totalMB} MB",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SpeakMindColors.textSecondary,
            )
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "You can leave the app. Download continues in background.",
        style = MaterialTheme.typography.bodySmall.copy(
            color = SpeakMindColors.textMuted,
        ),
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = SpeakMindColors.textSecondary,
        )
    ) {
        Text("Cancel")
    }
}

@Composable
private fun DownloadComplete() {
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = SpeakMindColors.success,
        modifier = Modifier.size(80.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "AI Model Ready!",
        style = MaterialTheme.typography.headlineMedium.copy(
            color = SpeakMindColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Your AI tutor is ready to chat.",
        style = MaterialTheme.typography.bodyLarge.copy(
            color = SpeakMindColors.textSecondary,
        )
    )
}

@Composable
private fun DownloadError(onRetry: () -> Unit) {
    Icon(
        Icons.Default.ErrorOutline,
        contentDescription = null,
        tint = SpeakMindColors.error,
        modifier = Modifier.size(64.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Download Failed",
        style = MaterialTheme.typography.headlineSmall.copy(
            color = SpeakMindColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Please check your WiFi connection and try again. The download will resume from where it stopped.",
        style = MaterialTheme.typography.bodyMedium.copy(
            color = SpeakMindColors.textSecondary,
        ),
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onRetry,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SpeakMindColors.neonCyan,
            contentColor = SpeakMindColors.backgroundDark,
        )
    ) {
        Text("Retry Download", fontWeight = FontWeight.Bold)
    }
}
