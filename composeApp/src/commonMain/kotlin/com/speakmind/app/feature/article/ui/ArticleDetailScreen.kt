package com.speakmind.app.feature.article.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.home.domain.model.getCategoryIcon
import com.speakmind.app.navigation.ArticleDetailDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.TtsSpeedButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.rememberInterstitialAdState
import androidx.compose.ui.graphics.Color
import com.speakmind.app.ui.theme.levelColorOf
import com.speakmind.app.ui.components.WordAction
import com.speakmind.app.ui.components.WordActionBottomSheet
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.SpeakMindColors
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
    val interstitialAd = rememberInterstitialAdState()

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

    val colors = com.speakmind.app.ui.theme.LocalSpeakMindColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
            }
            uiState.scenario == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Article not found",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = colors.textSecondary,
                        ),
                    )
                }
            }
            else -> {
                val scenario = uiState.scenario
                val levelColor = when (scenario.level) {
                    "A1" -> levelColorOf("A1")
                    "A2" -> levelColorOf("A1")
                    "B1" -> levelColorOf("B1")
                    "B2" -> levelColorOf("B1")
                    "C1" -> levelColorOf("C1")
                    else -> levelColorOf("A1")
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.sdp) // space for banner ad
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 52.sdp, bottom = 4.sdp)
                            .padding(horizontal = 8.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { interstitialAd.show(onDismissed = onBackClicked) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // Level & category badges
                        Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.sdp))
                                    .background(levelColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.sdp, vertical = 4.sdp),
                            ) {
                                Text(
                                    text = scenario.level,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = levelColor,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.sdp))
                                    .background(colors.neonCyan.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.sdp, vertical = 4.sdp),
                            ) {
                                Text(
                                    text = "${getCategoryIcon(scenario.category)} ${scenario.category}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = colors.neonCyan,
                                    ),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.sdp))
                    }

                    // Title
                    Text(
                        text = scenario.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(horizontal = 20.sdp, vertical = 8.sdp),
                    )

                    Spacer(modifier = Modifier.height(8.sdp))

                    // Listen button row with speed control
                    Row(
                        modifier = Modifier.padding(horizontal = 20.sdp),
                        horizontalArrangement = Arrangement.spacedBy(8.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ListenButton(
                            isSpeaking = uiState.isSpeaking,
                            onClick = onListenClicked,
                            modifier = Modifier.weight(1f),
                        )
                        TtsSpeedButton()
                    }

                    Spacer(modifier = Modifier.height(20.sdp))

                    // Article content card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.sdp)
                            .clip(RoundedCornerShape(20.sdp))
                            .background(colors.surface.copy(alpha = 0.8f))
                            .padding(horizontal = 20.sdp, vertical = 24.sdp),
                    ) {
                        Column {
                            Text(
                                text = "Tap any word for options",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colors.textMuted.copy(alpha = 0.5f),
                                ),
                            )
                            Spacer(modifier = Modifier.height(12.sdp))
                            TappableText(
                                text = scenario.emotionalStakes,
                                onWordClicked = onWordClicked,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.sdp))

                    // Discuss with Sage button
                    val accentColor = Color(0xFF7C4DFF)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.sdp)
                            .clip(RoundedCornerShape(16.sdp))
                            .background(colors.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.sdp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.sdp))
                            .clickable(onClick = onDiscussClicked)
                            .padding(16.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(32.sdp),
                        )
                        Spacer(modifier = Modifier.width(12.sdp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Discuss with Sage",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(8.sdp))
                                Text(
                                    text = "AI",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.sdp))
                                        .background(accentColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.sdp, vertical = 2.sdp),
                                )
                            }
                            Text(
                                text = "Chat about this article with Sage",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colors.textSecondary,
                                ),
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.sdp),
                        )
                    }

                    Spacer(modifier = Modifier.height(32.sdp))
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TappableText(
    text: String,
    onWordClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localColors = com.speakmind.app.ui.theme.LocalSpeakMindColors.current
    val words = text.split(Regex("(?<=\\s)|(?=\\s)"))
    val annotatedString = buildAnnotatedString {
        words.forEach { segment ->
            if (segment.isBlank()) {
                append(segment)
            } else {
                pushStringAnnotation(tag = "word", annotation = segment)
                withStyle(
                    SpanStyle(
                        color = localColors.textPrimary,
                        fontSize = 18.ssp,
                    )
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
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 32.ssp,
            fontSize = 18.ssp,
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations("word", offset, offset)
                .firstOrNull()?.let { annotation ->
                    onWordClicked(annotation.item)
                }
        },
    )
}

@Composable
private fun ListenButton(
    isSpeaking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val btnColors = com.speakmind.app.ui.theme.LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.sdp),
        shape = RoundedCornerShape(14.sdp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSpeaking) btnColors.neonCyan.copy(alpha = pulseAlpha)
            else btnColors.neonCyan,
            contentColor = btnColors.backgroundDark,
        ),
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Default.Stop
            else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(22.sdp),
        )
        Spacer(modifier = Modifier.width(8.sdp))
        Text(
            text = if (isSpeaking) "Stop Listening" else "Listen to Article",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

