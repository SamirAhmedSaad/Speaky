package com.speakmind.app.feature.flashcard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.db.Flashcards
import com.speakmind.app.navigation.FlashcardReviewDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.SpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.flashcardReviewScreen() {
    animatedComposable<FlashcardReviewDestination> {
        val viewModel = koinViewModel<FlashcardReviewViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        FlashcardReviewContent(
            uiState = uiState,
            onFlip = viewModel::onFlip,
            onRate = viewModel::onRate,
            onGoBack = viewModel::onGoBack,
        )
    }
}

@Composable
private fun FlashcardReviewContent(
    uiState: FlashcardReviewUiState,
    onFlip: () -> Unit,
    onRate: (String) -> Unit,
    onGoBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeakMindColors.backgroundGradient)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 52.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpeakMindColors.textPrimary
                )
            }
            Text(
                text = "Flashcard Review",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = SpeakMindColors.textPrimary,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(1f)
            )
            if (!uiState.isComplete) {
                Text(
                    text = "${uiState.reviewedCount}/${uiState.reviewedCount + uiState.remainingCount}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = SpeakMindColors.neonCyan
                    )
                )
            }
        }

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        color = SpeakMindColors.neonCyan,
                        modifier = Modifier.size(48.dp)
                    )
                }
                uiState.isComplete -> {
                    CompletionCard(reviewedCount = uiState.reviewedCount, onGoBack = onGoBack)
                }
                uiState.currentCard != null -> {
                    FlashcardContent(
                        card = uiState.currentCard,
                        isFlipped = uiState.isFlipped,
                        onFlip = onFlip,
                    )
                }
            }
        }

        // Rating buttons
        if (!uiState.isComplete && uiState.currentCard != null && uiState.isFlipped) {
            RatingButtons(onRate = onRate)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FlashcardContent(
    card: Flashcards,
    isFlipped: Boolean,
    onFlip: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400)
    )

    Box(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (rotation <= 90f) SpeakMindColors.surfaceVariant
                else SpeakMindColors.surface
            )
            .border(
                width = 1.dp,
                color = SpeakMindColors.neonCyan.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onFlip)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            // Front
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Tap to reveal",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = SpeakMindColors.textMuted
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = SpeakMindColors.neonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = card.sentence.replace(card.word, "___"),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = SpeakMindColors.textSecondary
                    ),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Back (mirrored content)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer { rotationY = 180f }
            ) {
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = SpeakMindColors.neonCyan,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = card.sentence,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = SpeakMindColors.textPrimary
                    ),
                    textAlign = TextAlign.Center
                )
                if (card.grammar_note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = card.grammar_note,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SpeakMindColors.gold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
                if (card.context.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.context,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SpeakMindColors.textMuted
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingButtons(onRate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RatingButton("Again", SpeakMindColors.error, Modifier.weight(1f)) { onRate("again") }
        RatingButton("Hard", SpeakMindColors.warning, Modifier.weight(1f)) { onRate("hard") }
        RatingButton("Good", SpeakMindColors.success, Modifier.weight(1f)) { onRate("good") }
        RatingButton("Easy", SpeakMindColors.neonCyan, Modifier.weight(1f)) { onRate("easy") }
    }
}

@Composable
private fun RatingButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.2f),
            contentColor = color
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun CompletionCard(reviewedCount: Int, onGoBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = SpeakMindColors.success,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (reviewedCount > 0) "Review Complete!" else "No Cards Due",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = SpeakMindColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (reviewedCount > 0) "You reviewed $reviewedCount cards" else "Come back later for more practice",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SpeakMindColors.textSecondary
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGoBack,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SpeakMindColors.neonCyan,
                contentColor = SpeakMindColors.backgroundDark
            )
        ) {
            Text("Back to Home", fontWeight = FontWeight.Bold)
        }
    }
}
