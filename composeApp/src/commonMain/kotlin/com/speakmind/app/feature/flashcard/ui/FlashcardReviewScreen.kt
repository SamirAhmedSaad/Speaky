package com.speakmind.app.feature.flashcard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import network.chaintech.sdpcomposemultiplatform.sdp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.db.Flashcards
import com.speakmind.app.navigation.FlashcardReviewDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.TtsSpeedButton
import com.speakmind.app.ui.components.rememberInterstitialAdState
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.flashcardReviewScreen() {
    animatedComposable<FlashcardReviewDestination> { backStackEntry ->
        val destination = backStackEntry.toRoute<FlashcardReviewDestination>()
        val viewModel = koinViewModel<FlashcardReviewViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(destination.showAllWords) {
            if (destination.showAllWords) viewModel.onTabSelected(FlashcardTab.ALL_WORDS)
        }
        FlashcardReviewContent(
            uiState = uiState,
            onCardClick = viewModel::onCardClicked,
            onSpeak = viewModel::onSpeak,
            onRate = viewModel::onRate,
            onDelete = viewModel::onDeleteCard,
            onGoBack = viewModel::onGoBack,
            onTabSelected = viewModel::onTabSelected,
        )
    }
}

@Composable
private fun FlashcardReviewContent(
    uiState: FlashcardReviewUiState,
    onCardClick: (Long) -> Unit,
    onSpeak: (String) -> Unit,
    onRate: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onGoBack: () -> Unit,
    onTabSelected: (FlashcardTab) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val interstitialAd = rememberInterstitialAdState()
    var lastAdShownAtCount by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.reviewedCount) {
        if (uiState.reviewedCount > 0 && uiState.reviewedCount % 5 == 0 && uiState.reviewedCount != lastAdShownAtCount) {
            lastAdShownAtCount = uiState.reviewedCount
            interstitialAd.show()
        }
    }

    LaunchedEffect(uiState.lastRatingMessage) {
        uiState.lastRatingMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.sdp)
                    .padding(top = 52.sdp, bottom = 8.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onGoBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "Review",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (uiState.selectedTab == FlashcardTab.DUE && !uiState.isComplete && uiState.cards.isNotEmpty()) {
                    Text(
                        text = "${uiState.cards.size} remaining",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.magenta,
                        ),
                    )
                    Spacer(modifier = Modifier.width(12.sdp))
                }
                if (uiState.selectedTab == FlashcardTab.ALL_WORDS && uiState.allCards.isNotEmpty()) {
                    Text(
                        text = "${uiState.allCards.size} words",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.neonCyan,
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.sdp))
                }
                TtsSpeedButton()
                Spacer(modifier = Modifier.width(4.sdp))
            }

            // Tabs
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                contentColor = colors.neonCyan,
                indicator = { tabPositions ->
                    if (uiState.selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal]),
                            color = colors.neonCyan,
                        )
                    }
                },
            ) {
                Tab(
                    selected = uiState.selectedTab == FlashcardTab.DUE,
                    onClick = { onTabSelected(FlashcardTab.DUE) },
                    text = {
                        Text(
                            text = "Due",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (uiState.selectedTab == FlashcardTab.DUE) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    },
                    selectedContentColor = colors.neonCyan,
                    unselectedContentColor = colors.textMuted,
                )
                Tab(
                    selected = uiState.selectedTab == FlashcardTab.ALL_WORDS,
                    onClick = { onTabSelected(FlashcardTab.ALL_WORDS) },
                    text = {
                        Text(
                            text = "All Words",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (uiState.selectedTab == FlashcardTab.ALL_WORDS) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    },
                    selectedContentColor = colors.neonCyan,
                    unselectedContentColor = colors.textMuted,
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.neonCyan)
                    }
                }
                uiState.selectedTab == FlashcardTab.ALL_WORDS -> {
                    if (uiState.allCards.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No words saved yet",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.textMuted,
                                ),
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
                            verticalArrangement = Arrangement.spacedBy(8.sdp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(uiState.allCards, key = { it.id }) { card ->
                                ReviewWordItem(
                                    card = card,
                                    isExpanded = uiState.expandedCardId == card.id,
                                    onClick = { onCardClick(card.id) },
                                    onSpeak = onSpeak,
                                    onRate = null,
                                    onDelete = { onDelete(card.id) },
                                )
                            }
                        }
                    }
                }
                uiState.isComplete -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompletionCard(reviewedCount = uiState.reviewedCount, onGoBack = onGoBack)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
                        verticalArrangement = Arrangement.spacedBy(8.sdp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(uiState.cards, key = { it.id }) { card ->
                            ReviewWordItem(
                                card = card,
                                isExpanded = uiState.expandedCardId == card.id,
                                onClick = { onCardClick(card.id) },
                                onSpeak = onSpeak,
                                onRate = { rating -> onRate(card.id, rating) },
                                onDelete = { onDelete(card.id) },
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnackbarHost(hostState = snackbarHostState)
            BannerAdView()
        }
    }
}

@Composable
private fun ReviewWordItem(
    card: Flashcards,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onSpeak: (String) -> Unit,
    onRate: ((String) -> Unit)?,
    onDelete: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = 1.sdp,
                color = if (isExpanded) colors.magenta.copy(alpha = 0.3f)
                else Color.Transparent,
                shape = RoundedCornerShape(12.sdp),
            )
    ) {
        // Word row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.sdp, vertical = 12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.neonCyan,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (card.context.isNotEmpty()) {
                    Text(
                        text = card.context,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.textMuted,
                        ),
                    )
                }
            }

            IconButton(
                onClick = { onSpeak(card.word) },
                modifier = Modifier.size(36.sdp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Play word",
                    tint = colors.magenta,
                    modifier = Modifier.size(20.sdp),
                )
            }
        }

        // Expanded content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.sdp, end = 16.sdp, bottom = 14.sdp),
            ) {
                HorizontalDivider(
                    color = colors.surfaceVariant,
                    modifier = Modifier.padding(bottom = 10.sdp),
                )

                // Example sentence
                if (card.sentence.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = card.sentence,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textPrimary,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onSpeak(card.sentence) },
                            modifier = Modifier.size(32.sdp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Play sentence",
                                tint = colors.neonCyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.sdp),
                            )
                        }
                    }
                }

                // Grammar note
                if (card.grammar_note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.sdp))
                    Text(
                        text = card.grammar_note,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.gold,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(12.sdp))

                // Rating buttons (only in Due tab)
                if (onRate != null) {
                    Text(
                        text = "How well do you know this?",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.textMuted,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.sdp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.sdp),
                    ) {
                        RatingChip("Again", colors.error, Modifier.weight(1f)) { onRate("again") }
                        RatingChip("Hard", colors.warning, Modifier.weight(1f)) { onRate("hard") }
                        RatingChip("Good", colors.success, Modifier.weight(1f)) { onRate("good") }
                        RatingChip("Easy", colors.neonCyan, Modifier.weight(1f)) { onRate("easy") }
                    }
                    Spacer(modifier = Modifier.height(8.sdp))
                }

                // Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.sdp))
                        .clickable(onClick = onDelete)
                        .padding(vertical = 6.sdp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.sdp),
                    )
                    Spacer(modifier = Modifier.width(4.sdp))
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.textMuted,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingChip(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.sdp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.sdp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = color,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun CompletionCard(reviewedCount: Int, onGoBack: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.sdp),
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(64.sdp),
        )
        Spacer(modifier = Modifier.height(16.sdp))
        Text(
            text = if (reviewedCount > 0) "Review Complete!" else "No Cards Due",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(8.sdp))
        Text(
            text = if (reviewedCount > 0) "You reviewed $reviewedCount cards" else "Come back later for more practice",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.textSecondary,
            ),
        )
        Spacer(modifier = Modifier.height(24.sdp))
        Button(
            onClick = onGoBack,
            shape = RoundedCornerShape(16.sdp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.neonCyan,
                contentColor = colors.backgroundDark,
            ),
        ) {
            Text("Back to Home", fontWeight = FontWeight.Bold)
        }
    }
}
