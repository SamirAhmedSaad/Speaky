package com.speakmind.app.feature.story.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.speakmind.app.feature.story.domain.model.StoryTopic
import com.speakmind.app.navigation.StoryDetailDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.TtsSpeedButton
import com.speakmind.app.ui.components.WordAction
import com.speakmind.app.ui.components.WordActionBottomSheet
import com.speakmind.app.ui.components.rememberInterstitialAdState
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.storyDetailScreen() {
    animatedComposable<StoryDetailDestination> { backStackEntry ->
        val destination = backStackEntry.arguments?.getLong("storyId") ?: 0L
        val viewModel = koinViewModel<StoryDetailViewModel> { parametersOf(destination) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        StoryDetailContent(
            uiState = uiState,
            onBackClicked = viewModel::onBackClicked,
            onSpeakClicked = viewModel::onSpeakClicked,
            onWordClicked = viewModel::onWordClicked,
            onDismissWord = viewModel::onDismissWord,
            onSaveWord = viewModel::onSaveWordToFlashcard,
            onActionSelected = viewModel::onActionSelected,
            onSpeakWord = viewModel::onSpeakWord,
        )
    }
}

@Composable
private fun StoryDetailContent(
    uiState: StoryDetailUiState,
    onBackClicked: () -> Unit,
    onSpeakClicked: () -> Unit,
    onWordClicked: (String) -> Unit,
    onDismissWord: () -> Unit,
    onSaveWord: () -> Unit,
    onActionSelected: (WordAction) -> Unit,
    onSpeakWord: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val interstitialAd = rememberInterstitialAdState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.wordSaved) {
        if (uiState.wordSaved) snackbarHostState.showSnackbar("Saved to flashcards!")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
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

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
            }
            uiState.story == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Story not found",
                        style = MaterialTheme.typography.bodyLarge.copy(color = colors.textSecondary),
                    )
                }
            }
            else -> {
                val story = uiState.story
                val topic = StoryTopic.fromLabel(story.category)
                val topicColor = topicColor(topic)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
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
                        if (story.category.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.sdp))
                                    .background(topicColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.sdp, vertical = 4.sdp),
                            ) {
                                Text(
                                    text = story.category,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = topicColor,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.sdp))
                    }

                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(horizontal = 20.sdp, vertical = 8.sdp),
                    )

                    if (story.pubDate.isNotEmpty()) {
                        Text(
                            text = formatDate(story.pubDate),
                            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                            modifier = Modifier.padding(horizontal = 20.sdp),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.sdp))

                    Row(
                        modifier = Modifier.padding(horizontal = 20.sdp),
                        horizontalArrangement = Arrangement.spacedBy(8.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ListenButton(
                            isSpeaking = uiState.isSpeaking,
                            onClick = onSpeakClicked,
                            modifier = Modifier.weight(1f),
                        )
                        TtsSpeedButton()
                    }

                    Spacer(modifier = Modifier.height(20.sdp))

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
                                text = stripLeadingDate(story.content).stripMarkdown(),
                                onWordClicked = onWordClicked,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.sdp))
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
private fun TappableText(
    text: String,
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
                pushStringAnnotation(tag = "word", annotation = segment)
                withStyle(SpanStyle(color = colors.textPrimary, fontSize = 18.ssp)) {
                    append(segment)
                }
                pop()
            }
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 32.ssp, fontSize = 18.ssp),
        onClick = { offset ->
            annotatedString.getStringAnnotations("word", offset, offset)
                .firstOrNull()?.let { onWordClicked(it.item) }
        },
    )
}

private fun stripLeadingDate(text: String): String {
    return text
        .replace(Regex("^\\d{1,2}[.\\-/]\\d{1,2}[.\\-/]\\d{4}\\s*\\d{0,2}:?\\d{0,2}\\s*[–\\-]?\\s*"), "")
        .replace(Regex("^\\w+\\s+\\d{1,2},?\\s+\\d{4}\\s*[–\\-]?\\s*"), "")
        .trim()
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
            containerColor = if (isSpeaking) colors.neonCyan.copy(alpha = pulseAlpha) else colors.neonCyan,
            contentColor = colors.backgroundDark,
        ),
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(22.sdp),
        )
        Spacer(modifier = Modifier.width(8.sdp))
        Text(
            text = if (isSpeaking) "Stop Listening" else "Listen to Story",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

private fun formatDate(pubDate: String): String {
    return try {
        val parts = pubDate.split(" ")
        if (parts.size >= 4) "${parts[1]} ${parts[2]} ${parts[3]}" else pubDate
    } catch (_: Exception) {
        pubDate
    }
}
