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
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.ModelDownloadDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.theme.LocalSpeakMindColors
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
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        // Back button
        IconButton(
            onClick = onGoBack,
            modifier = Modifier
                .padding(top = 48.sdp, start = 8.sdp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.textPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                uiState.isComplete -> DownloadComplete()
                uiState.isError -> DownloadError(errorMessage = uiState.errorMessage, onRetry = onRetry)
                uiState.hasStarted || uiState.isDownloading -> DownloadProgress(uiState = uiState, onCancel = onCancel)
                else -> DownloadPrompt(onStartDownload = onStartDownload)
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DownloadPrompt(onStartDownload: () -> Unit) {
    val colors = LocalSpeakMindColors.current
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
        modifier = Modifier.size((120 * iconScale).sdp)
    ) {
        Box(
            modifier = Modifier
                .size(100.sdp)
                .clip(CircleShape)
                .background(colors.neonCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = colors.neonCyan,
                modifier = Modifier.size(48.sdp)
            )
        }
    }

    Spacer(modifier = Modifier.height(32.sdp))

    Text(
        text = "AI Model Required",
        style = MaterialTheme.typography.headlineMedium.copy(
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(12.sdp))

    Text(
        text = "To have real conversations, SpeakMind needs to download an AI model. This is a one-time download.",
        style = MaterialTheme.typography.bodyLarge.copy(
            color = colors.textSecondary,
            lineHeight = 24.ssp,
        ),
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.sdp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.sdp, vertical = 10.sdp)
    ) {
        Icon(
            Icons.Default.Wifi,
            contentDescription = null,
            tint = colors.neonCyan,
            modifier = Modifier.size(18.sdp)
        )
        Spacer(modifier = Modifier.width(8.sdp))
        Text(
            text = "WiFi only \u2022 Can resume if interrupted",
            style = MaterialTheme.typography.bodySmall.copy(
                color = colors.textMuted
            )
        )
    }

    Spacer(modifier = Modifier.height(40.sdp))

    Button(
        onClick = onStartDownload,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.sdp),
        shape = RoundedCornerShape(16.sdp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.neonCyan,
            contentColor = colors.backgroundDark,
        )
    ) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(20.sdp)
        )
        Spacer(modifier = Modifier.width(8.sdp))
        Text(
            text = "Download AI Model",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun DownloadProgress(uiState: DownloadUiState, onCancel: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress / 100f,
        animationSpec = tween(300)
    )

    // Circular progress
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.sdp)
    ) {
        Canvas(modifier = Modifier.size(140.sdp)) {
            // Background circle
            drawArc(
                color = colors.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(colors.neonCyan, colors.magenta)
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
                    color = colors.neonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.ssp,
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(24.sdp))

    Text(
        text = if (uiState.waitingForWifi) "Waiting for WiFi connection..."
               else "Downloading AI Model...",
        style = MaterialTheme.typography.titleMedium.copy(
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(8.sdp))

    if (uiState.totalMB > 0) {
        Text(
            text = "${uiState.downloadedMB} MB / ${uiState.totalMB} MB",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.textSecondary,
            )
        )
    }

    Spacer(modifier = Modifier.height(8.sdp))

    Text(
        text = "You can leave the app. Download continues in background.",
        style = MaterialTheme.typography.bodySmall.copy(
            color = colors.textMuted,
        ),
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.sdp))

    OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(12.sdp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textSecondary,
        )
    ) {
        Text("Cancel")
    }
}

@Composable
private fun DownloadComplete() {
    val colors = LocalSpeakMindColors.current
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = colors.success,
        modifier = Modifier.size(80.sdp)
    )

    Spacer(modifier = Modifier.height(24.sdp))

    Text(
        text = "AI Model Ready!",
        style = MaterialTheme.typography.headlineMedium.copy(
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(8.sdp))

    Text(
        text = "Your AI tutor is ready to chat.",
        style = MaterialTheme.typography.bodyLarge.copy(
            color = colors.textSecondary,
        )
    )
}

@Composable
private fun DownloadError(errorMessage: String?, onRetry: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Icon(
        Icons.Default.ErrorOutline,
        contentDescription = null,
        tint = colors.error,
        modifier = Modifier.size(64.sdp)
    )

    Spacer(modifier = Modifier.height(24.sdp))

    Text(
        text = "Download Failed",
        style = MaterialTheme.typography.headlineSmall.copy(
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    )

    Spacer(modifier = Modifier.height(8.sdp))

    val displayMessage = errorMessage
        ?: "Please check your WiFi connection and try again. The download will resume from where it stopped."

    Text(
        text = displayMessage,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = colors.textSecondary,
        ),
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.sdp))

    // Only show retry for recoverable errors (not permanent server rejections)
    val isPermanentError = errorMessage != null &&
        (errorMessage.contains("401") || errorMessage.contains("403") ||
         errorMessage.contains("404") || errorMessage.contains("410"))

    if (!isPermanentError) {
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(16.sdp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.neonCyan,
                contentColor = colors.backgroundDark,
            )
        ) {
            Text("Retry Download", fontWeight = FontWeight.Bold)
        }
    }
}
