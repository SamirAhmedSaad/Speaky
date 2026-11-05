package com.speakmind.app.feature.flashcard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.db.Flashcards
import com.speakmind.app.feature.flashcard.domain.SM2Engine
import com.speakmind.app.navigation.FlashcardReviewDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyFitText
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.SpeakyProgress
import com.speakmind.app.ui.components.SpeakySpeedButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

fun NavGraphBuilder.flashcardReviewScreen() {
    animatedComposable<FlashcardReviewDestination> { backStackEntry ->
        val destination = backStackEntry.toRoute<FlashcardReviewDestination>()
        val viewModel = koinViewModel<FlashcardReviewViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        FlashcardReviewContent(
            uiState = uiState,
            onCardClick = viewModel::onCardClicked,
            onSpeak = viewModel::onSpeak,
            onRate = viewModel::onRate,
            onDelete = viewModel::onDeleteCard,
            onGoBack = viewModel::onGoBack,
            savedOnly = destination.showAllWords,
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
    /** Saved words list instead of the review; the two are separate screens. */
    savedOnly: Boolean,
) {
    val colors = LocalSpeakMindColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.lastRatingMessage) {
        uiState.lastRatingMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpeakyBackButton(onClick = onGoBack)
                Text(
                    if (savedOnly) "Saved words" else "Flashcards",
                    Modifier.weight(1f),
                    style = displayStyle(26.sp),
                    color = colors.textPrimary,
                )
                if (savedOnly && uiState.allCards.isNotEmpty()) {
                    Text("${uiState.allCards.size}", color = colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                SpeakySpeedButton()
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.neonCyan)
                    }
                    savedOnly -> SavedWords(
                        cards = uiState.allCards,
                        expandedCardId = uiState.expandedCardId,
                        onCardClick = onCardClick,
                        onSpeak = onSpeak,
                        onDelete = onDelete,
                    )
                    uiState.isComplete -> CaughtUp(reviewedCount = uiState.reviewedCount, onGoBack = onGoBack)
                    else -> {
                        val card = uiState.cards.first()
                        DueCard(
                            card = card,
                            revealed = uiState.expandedCardId == card.id,
                            reviewed = uiState.reviewedCount,
                            total = uiState.reviewedCount + uiState.cards.size,
                            onFlip = { onCardClick(card.id) },
                            onSpeak = onSpeak,
                            onRate = { onRate(card.id, it) },
                            onDelete = { onDelete(card.id) },
                        )
                    }
                }
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
            }

            BannerAdView(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** One due card at a time: tap to flip, then rate it. */
@Composable
private fun DueCard(
    card: Flashcards,
    revealed: Boolean,
    reviewed: Int,
    total: Int,
    onFlip: () -> Unit,
    onSpeak: (String) -> Unit,
    onRate: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeakyProgress(
                progress = if (total > 0) reviewed.toFloat() / total else 0f,
                color = ReviewGreen,
                height = 10.dp,
                modifier = Modifier.weight(1f),
            )
            Text("$reviewed/$total", color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            stageLabel(card.repetitions),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.coralSoft)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = colors.onCoralSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x1A17142B), spotColor = Color(0x1A17142B))
                .clip(RoundedCornerShape(32.dp))
                .background(colors.surface)
                .clickable(role = Role.Button, onClick = onFlip)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            SpeakyFitText(
                card.word,
                style = displayStyle(44.sp, lineHeight = 48.sp),
                color = colors.neonCyan,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (card.context.isNotEmpty()) {
                Text(card.context, color = colors.textMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            SpeakyIconButton(
                icon = SpeakyIcons.Speaker,
                contentDescription = "Play word",
                onClick = { onSpeak(card.word) },
                iconSize = 18.dp,
                background = colors.primarySoft,
                tint = colors.neonCyan,
            )
            if (revealed) {
                Box(Modifier.width(64.dp).height(2.dp).background(colors.track))
                if (card.grammar_note.isNotEmpty()) {
                    Text(
                        card.grammar_note,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                if (card.sentence.isNotEmpty()) {
                    Text(
                        "“${card.sentence}”",
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSpeak(card.sentence) }.padding(4.dp),
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    "Remove card",
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(6.dp),
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    "Tap to see the meaning",
                    modifier = Modifier.padding(top = 24.dp),
                    color = colors.textMuted,
                    fontSize = 14.sp,
                )
            }
        }

        if (revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingButton("Again", nextReviewLabel(card, "again"), colors.errorSoft(), colors.error, Modifier.weight(1f)) { onRate("again") }
                RatingButton("Hard", nextReviewLabel(card, "hard"), colors.coralSoft, colors.onCoralSoft, Modifier.weight(1f)) { onRate("hard") }
                RatingButton("Good", nextReviewLabel(card, "good"), colors.primarySoft, colors.onPrimarySoft, Modifier.weight(1f)) { onRate("good") }
                RatingButton("Easy", nextReviewLabel(card, "easy"), colors.mintSoft, colors.onMintSoft, Modifier.weight(1f)) { onRate("easy") }
            }
        } else {
            SpeakyPrimaryButton(
                text = "Show answer",
                onClick = onFlip,
                height = 60.dp,
                radius = 20.dp,
                background = colors.textPrimary,
                color = colors.backgroundDark,
            )
        }
    }
}

@Composable
private fun RatingButton(label: String, whenLabel: String, background: Color, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(whenLabel, color = color.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

@Composable
private fun CaughtUp(reviewedCount: Int, onGoBack: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(colors.mintSoft), contentAlignment = Alignment.Center) {
            Icon(SpeakyIcons.Check, contentDescription = null, tint = colors.onMintSoft, modifier = Modifier.size(44.dp))
        }
        Text(
            if (reviewedCount > 0) "Review complete!" else "All caught up!",
            style = displayStyle(24.sp),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            if (reviewedCount > 0) if (reviewedCount == 1) "You reviewed 1 card. We'll bring it back when it's due."
            else "You reviewed $reviewedCount cards. We'll bring them back when they're due."
            else "No words due right now. Come back tomorrow, or look through your saved words.",
            color = colors.textSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        SpeakyPrimaryButton(text = "Back to Home", onClick = onGoBack, height = 54.dp, radius = 16.dp)
    }
}

@Composable
private fun SavedWords(
    cards: List<Flashcards>,
    expandedCardId: Long?,
    onCardClick: (Long) -> Unit,
    onSpeak: (String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    if (cards.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
            Text(
                "No words saved yet.\nTap \"Mark as learned\" in Flash cards, or tap any word in Daily topics, to save it here.",
                color = colors.textSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.id }) { card ->
            SavedWordRow(
                card = card,
                isExpanded = expandedCardId == card.id,
                onClick = { onCardClick(card.id) },
                onSpeak = onSpeak,
                onDelete = { onDelete(card.id) },
            )
        }
    }
}

@Composable
private fun SavedWordRow(
    card: Flashcards,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onSpeak: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    // Word Builder keeps the meaning in grammar_note; other sources only have the sentence.
    val meaning = card.grammar_note.ifBlank { card.sentence }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.word, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                if (meaning.isNotEmpty() && !isExpanded) {
                    Text(meaning, color = colors.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            sourceTag(card.context)?.let {
                Text(
                    it,
                    modifier = Modifier
                        .widthIn(max = 130.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.primarySoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = colors.onPrimarySoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card.grammar_note.isNotEmpty()) {
                    Text(card.grammar_note, color = colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                }
                if (card.sentence.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.backgroundDark)
                            .clickable { onSpeak(card.sentence) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(card.sentence, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(SpeakyIcons.Speaker, contentDescription = "Play sentence", tint = colors.faint, modifier = Modifier.size(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(SpeakyIcons.Speaker, "Listen", colors.primarySoft, colors.onPrimarySoft) { onSpeak(card.word) }
                    SmallAction(SpeakyIcons.Trash, "Remove", colors.backgroundDark, colors.textMuted, onDelete)
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private val ReviewGreen = Color(0xFF19B88A)

@Composable
private fun com.speakmind.app.ui.theme.SpeakMindThemeColors.errorSoft(): Color = error.copy(alpha = 0.14f)

/** Where the card came from, from the context the saving screen wrote. */
private fun sourceTag(context: String): String? = when {
    context.isBlank() -> null
    context.startsWith("Word Builder:") -> context.removePrefix("Word Builder:").trim() + " list"
    context.startsWith("Article:") -> "Article"
    context.startsWith("Story:") -> "Story"
    else -> context
}

private fun stageLabel(repetitions: Long): String {
    if (repetitions <= 0L) return "NEW WORD"
    val n = repetitions + 1
    val ordinal = when {
        n % 100 in 11..13 -> "${n}TH"
        n % 10 == 1L -> "${n}ST"
        n % 10 == 2L -> "${n}ND"
        n % 10 == 3L -> "${n}RD"
        else -> "${n}TH"
    }
    return (if (repetitions < 3) "LEARNING" else "FAMILIAR") + " · $ordinal REVIEW"
}

/** The interval the SM-2 engine would give for this rating, as the button's hint. */
private fun nextReviewLabel(card: Flashcards, rating: String): String {
    val days = SM2Engine.review(
        quality = SM2Engine.qualityFromRating(rating),
        repetitions = card.repetitions,
        easeFactor = card.ease_factor,
        intervalDays = card.interval_days,
    ).nextIntervalDays.roundToInt().coerceAtLeast(1)
    return if (days == 1) "1 day" else "$days days"
}
