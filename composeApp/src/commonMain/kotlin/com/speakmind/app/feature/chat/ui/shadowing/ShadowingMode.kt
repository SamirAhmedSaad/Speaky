package com.speakmind.app.feature.chat.ui.shadowing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import com.speakmind.app.feature.learning.domain.PronunciationResult
import com.speakmind.app.feature.learning.domain.PronunciationScorer
import com.speakmind.app.feature.learning.domain.WordMatch
import com.speakmind.app.ui.theme.LocalSpeakMindColors

enum class ShadowingState { IDLE, LISTENING_AI, RECORDING_USER, SHOWING_RESULT }

@Composable
fun ShadowingOverlay(
    targetSentence: String,
    onDismiss: () -> Unit,
    onPlayAi: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    spokenText: String?,
    state: ShadowingState,
) {
    val colors = LocalSpeakMindColors.current
    val result = remember(spokenText) {
        if (spokenText != null && state == ShadowingState.SHOWING_RESULT) {
            PronunciationScorer.score(targetSentence, spokenText)
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDark.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.sdp)
        ) {
            // Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary
                    )
                }
            }

            Text(
                text = "Shadowing Mode",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = colors.neonCyan,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.sdp))

            Text(
                text = "Listen, then repeat",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary
                )
            )

            Spacer(modifier = Modifier.height(32.sdp))

            // Target sentence
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .padding(20.sdp)
            ) {
                Text(
                    text = targetSentence,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.textPrimary,
                        lineHeight = 28.ssp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.sdp))

            // Result display
            if (result != null) {
                ScoreDisplay(result)
                Spacer(modifier = Modifier.height(16.sdp))
                WordMatchDisplay(result.matchedWords)
                Spacer(modifier = Modifier.height(16.sdp))
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.sdp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play AI button
                IconButton(
                    onClick = onPlayAi,
                    modifier = Modifier
                        .size(56.sdp)
                        .clip(CircleShape)
                        .background(colors.surfaceVariant)
                ) {
                    Icon(
                        if (state == ShadowingState.LISTENING_AI) Icons.Default.Replay
                        else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = colors.neonCyan,
                        modifier = Modifier.size(28.sdp)
                    )
                }

                // Record button
                Button(
                    onClick = {
                        if (state == ShadowingState.RECORDING_USER) onStopRecording()
                        else onStartRecording()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state == ShadowingState.RECORDING_USER)
                            colors.magenta else colors.neonCyan,
                        contentColor = colors.backgroundDark
                    ),
                    modifier = Modifier.size(72.sdp),
                    contentPadding = PaddingValues(0.sdp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Record",
                        modifier = Modifier.size(32.sdp)
                    )
                }
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(16.sdp))
                Text(
                    text = result.feedback,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ScoreDisplay(result: PronunciationResult) {
    val colors = LocalSpeakMindColors.current
    val scoreColor = when {
        result.score >= 80 -> colors.success
        result.score >= 50 -> colors.warning
        else -> colors.error
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.sdp)
            .clip(CircleShape)
            .background(scoreColor.copy(alpha = 0.15f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${result.score}",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = scoreColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.ssp
                )
            )
            Text(
                text = "/100",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = scoreColor.copy(alpha = 0.7f)
                )
            )
        }
    }
}

@Composable
private fun WordMatchDisplay(matches: List<WordMatch>) {
    val colors = LocalSpeakMindColors.current
    val annotatedString = buildAnnotatedString {
        matches.forEachIndexed { index, match ->
            if (index > 0) append(" ")
            val color = if (match.isCorrect) colors.success else colors.error
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) {
                append(match.expected)
            }
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
