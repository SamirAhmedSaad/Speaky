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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.feature.learning.domain.PronunciationResult
import com.speakmind.app.feature.learning.domain.PronunciationScorer
import com.speakmind.app.feature.learning.domain.WordMatch
import com.speakmind.app.ui.theme.SpeakMindColors

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
    val result = remember(spokenText) {
        if (spokenText != null && state == ShadowingState.SHOWING_RESULT) {
            PronunciationScorer.score(targetSentence, spokenText)
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeakMindColors.backgroundDark.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
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
                        tint = SpeakMindColors.textSecondary
                    )
                }
            }

            Text(
                text = "Shadowing Mode",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = SpeakMindColors.neonCyan,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Listen, then repeat",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SpeakMindColors.textSecondary
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Target sentence
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SpeakMindColors.surfaceVariant.copy(alpha = 0.5f))
                    .padding(20.dp)
            ) {
                Text(
                    text = targetSentence,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = SpeakMindColors.textPrimary,
                        lineHeight = 28.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Result display
            if (result != null) {
                ScoreDisplay(result)
                Spacer(modifier = Modifier.height(16.dp))
                WordMatchDisplay(result.matchedWords)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play AI button
                IconButton(
                    onClick = onPlayAi,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SpeakMindColors.surfaceVariant)
                ) {
                    Icon(
                        if (state == ShadowingState.LISTENING_AI) Icons.Default.Replay
                        else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = SpeakMindColors.neonCyan,
                        modifier = Modifier.size(28.dp)
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
                            SpeakMindColors.magenta else SpeakMindColors.neonCyan,
                        contentColor = SpeakMindColors.backgroundDark
                    ),
                    modifier = Modifier.size(72.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Record",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = result.feedback,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = SpeakMindColors.textSecondary
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ScoreDisplay(result: PronunciationResult) {
    val scoreColor = when {
        result.score >= 80 -> SpeakMindColors.success
        result.score >= 50 -> SpeakMindColors.warning
        else -> SpeakMindColors.error
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(scoreColor.copy(alpha = 0.15f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${result.score}",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = scoreColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
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
    val annotatedString = buildAnnotatedString {
        matches.forEachIndexed { index, match ->
            if (index > 0) append(" ")
            val color = if (match.isCorrect) SpeakMindColors.success else SpeakMindColors.error
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
