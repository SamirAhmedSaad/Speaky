package com.speakmind.app.feature.article.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.ArticleDetailDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPill
import com.speakmind.app.ui.components.SpeakySpeedButton
import com.speakmind.app.ui.components.WordAction
import com.speakmind.app.ui.components.WordActionBottomSheet
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.articleDetailScreen() {
    animatedComposable<ArticleDetailDestination> { backStackEntry ->
        val scenarioId = backStackEntry.arguments?.getString("scenarioId") ?: ""
        val viewModel = koinViewModel<ArticleDetailViewModel> { parametersOf(scenarioId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        ArticleDetailContent(
            uiState = uiState,
            onBackClicked = viewModel::onBackClicked,
            onListenClicked = viewModel::onListenClicked,
            onDiscussClicked = viewModel::onDiscussClicked,
            onWordClicked = viewModel::onWordClicked,
            onDismissWord = viewModel::onDismissWord,
            onSaveWord = viewModel::onSaveWordToFlashcard,
            onActionSelected = viewModel::onActionSelected,
            onSpeakWord = viewModel::onSpeakWord,
        )
    }
}

@Composable
private fun ArticleDetailContent(
    uiState: ArticleDetailUiState,
    onBackClicked: () -> Unit,
    onListenClicked: () -> Unit,
    onDiscussClicked: () -> Unit,
    onWordClicked: (String) -> Unit,
    onDismissWord: () -> Unit,
    onSaveWord: () -> Unit,
    onActionSelected: (WordAction) -> Unit,
    onSpeakWord: () -> Unit,
) {
    if (uiState.selectedWord != null) {
        WordActionBottomSheet(
            word = uiState.selectedWord,
            wordSaved = uiState.wordSaved,
            selectedAction = uiState.selectedAction,
            meaningText = uiState.meaningText,
            partOfSpeech = uiState.partOfSpeech,
            translationText = uiState.translationText,
            isLoadingAction = uiState.isLoadingAction,
            onActionSelected = onActionSelected,
            onSaveWord = onSaveWord,
            onSpeakWord = onSpeakWord,
            onDismiss = onDismissWord,
        )
    }

    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
                uiState.scenario == null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    SpeakyBackButton(onClick = onBackClicked)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Article not found", color = colors.textSecondary, fontSize = 15.sp)
                    }
                }
                else -> {
                    val scenario = uiState.scenario
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SpeakyBackButton(onClick = onBackClicked)
                            Spacer(Modifier.weight(1f))
                            val pillPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            if (scenario.level.isNotBlank()) {
                                SpeakyPill(scenario.level, colors.goldSoft, colors.onGoldSoft, fontSize = 12.sp, padding = pillPadding)
                            }
                            if (scenario.category.isNotBlank()) {
                                SpeakyPill(scenario.category, colors.primarySoft, colors.onPrimarySoft, fontSize = 12.sp, padding = pillPadding)
                            }
                        }

                        Text(scenario.title, style = displayStyle(28.sp, lineHeight = 31.sp), color = colors.textPrimary)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ListenButton(
                                isSpeaking = uiState.isSpeaking,
                                onClick = onListenClicked,
                                modifier = Modifier.weight(1f),
                            )
                            SpeakySpeedButton(
                                modifier = Modifier.width(56.dp),
                                height = 50.dp,
                                radius = 16.dp,
                                fontSize = 14.sp,
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(colors.surface)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Tap any word", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            TappableText(
                                text = scenario.emotionalStakes,
                                selectedWord = uiState.selectedWord,
                                onWordClicked = onWordClicked,
                            )
                        }

                        if (com.speakmind.app.config.Features.AI_CHAT) {
                            DiscussCard(onClick = onDiscussClicked)
                        }
                    }
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun TappableText(
    text: String,
    selectedWord: String?,
    onWordClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpeakMindColors.current
    val words = text.split(Regex("(?<=\\s)|(?=\\s)"))
    val annotatedString = buildAnnotatedString {
        words.forEach { segment ->
            if (segment.isBlank()) {
                append(segment)
            } else {
                // The word open in the sheet is marked like the design's selected chip.
                val selected = selectedWord != null &&
                    segment.replace(Regex("[^a-zA-Z'-]"), "").equals(selectedWord, ignoreCase = true)
                pushStringAnnotation(tag = "word", annotation = segment)
                withStyle(
                    if (selected) SpanStyle(color = colors.onPrimary, background = colors.neonCyan)
                    else SpanStyle(color = colors.textPrimary),
                ) {
                    append(segment)
                }
                pop()
            }
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp),
        onClick = { offset ->
            annotatedString.getStringAnnotations("word", offset, offset)
                .firstOrNull()?.let { annotation -> onWordClicked(annotation.item) }
        },
    )
}

@Composable
private fun ListenButton(
    isSpeaking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSpeaking) colors.neonCyan.copy(alpha = pulseAlpha) else colors.neonCyan)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSpeaking) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(colors.onPrimary))
        } else {
            Icon(SpeakyIcons.Speaker, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
        }
        Text(
            if (isSpeaking) "Stop listening" else "Listen to article",
            color = colors.onPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun DiscussCard(onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpeakyIcons.Sparkle, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Discuss with Sage", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("Chat about this article with Sage", color = colors.textMuted, fontSize = 13.sp)
        }
        Icon(SpeakyIcons.Chevron, contentDescription = null, tint = colors.faint, modifier = Modifier.size(20.dp))
    }
}
