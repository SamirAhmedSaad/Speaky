package com.speakmind.app.feature.vocabulary.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import network.chaintech.sdpcomposemultiplatform.sdp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.ui.theme.levelColorOf
import com.speakmind.app.feature.vocabulary.domain.model.VocabWord
import com.speakmind.app.navigation.VocabWordListDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.TtsSpeedButton
import com.speakmind.app.ui.components.rememberInterstitialAdState
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.vocabWordListScreen() {
    animatedComposable<VocabWordListDestination> { backStackEntry ->
        val level = backStackEntry.arguments?.getString("level") ?: "A1"
        val viewModel = koinViewModel<VocabWordListViewModel> { parametersOf(level) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
        VocabWordListContent(
            uiState = uiState,
            isSpeaking = isSpeaking,
            onWordClick = viewModel::onWordClicked,
            onSpeak = viewModel::onSpeak,
            onGoBack = viewModel::onGoBack,
            onMarkLearned = viewModel::onMarkLearned,
        )
    }
}

@Composable
private fun VocabWordListContent(
    uiState: VocabWordListUiState,
    isSpeaking: Boolean,
    onWordClick: (Int) -> Unit,
    onSpeak: (String) -> Unit,
    onGoBack: () -> Unit,
    onMarkLearned: (VocabWord) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val levelColor = levelColorOf(uiState.level)
    val interstitialAd = rememberInterstitialAdState()
    var wordClickCount by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.sdp, bottom = 8.sdp)
                    .padding(horizontal = 12.sdp),
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
                    text = uiState.levelTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                TtsSpeedButton()
                Spacer(modifier = Modifier.width(4.sdp))
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
            } else {
                // Word count header
                Text(
                    text = "${uiState.words.size} words",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = levelColor,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(horizontal = 20.sdp, vertical = 4.sdp),
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
                    verticalArrangement = Arrangement.spacedBy(8.sdp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(uiState.words) { index, word ->
                        WordItem(
                            word = word,
                            isExpanded = uiState.expandedWordIndex == index,
                            isLearned = word.word in uiState.learnedWords,
                            levelColor = levelColor,
                            onClick = {
                                wordClickCount++
                                if (wordClickCount % 5 == 0) {
                                    interstitialAd.show()
                                }
                                onWordClick(index)
                            },
                            onSpeak = onSpeak,
                            onMarkLearned = { onMarkLearned(word) },
                        )
                    }
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun WordItem(
    word: VocabWord,
    isExpanded: Boolean,
    isLearned: Boolean,
    levelColor: Color,
    onClick: () -> Unit,
    onSpeak: (String) -> Unit,
    onMarkLearned: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val learnedGreen = Color(0xFF4CAF50)
    val borderColor = when {
        isLearned -> learnedGreen.copy(alpha = 0.5f)
        isExpanded -> colors.neonCyan.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    val wordTextColor = if (isLearned) learnedGreen else colors.neonCyan

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(
                if (isLearned) learnedGreen.copy(alpha = 0.08f)
                else colors.surfaceVariant.copy(alpha = 0.6f)
            )
            .border(
                width = 1.sdp,
                color = borderColor,
                shape = RoundedCornerShape(12.sdp),
            )
    ) {
        // Word row (always visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.sdp, vertical = 12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = wordTextColor,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = word.partOfSpeech,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.textMuted,
                    ),
                )
            }

            if (isLearned) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Learned",
                    tint = learnedGreen,
                    modifier = Modifier.size(20.sdp),
                )
                Spacer(modifier = Modifier.width(4.sdp))
            }

            IconButton(
                onClick = { onSpeak(word.word) },
                modifier = Modifier.size(36.sdp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Play word",
                    tint = levelColor,
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
                // Meaning
                HorizontalDivider(
                    color = colors.surfaceVariant,
                    modifier = Modifier.padding(bottom = 10.sdp),
                )
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary,
                    ),
                )

                Spacer(modifier = Modifier.height(12.sdp))

                // Sentences
                word.sentences.forEach { sentence ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sentence,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = colors.textPrimary,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onSpeak(sentence) },
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

                Spacer(modifier = Modifier.height(8.sdp))

                // Mark as Learned button
                OutlinedButton(
                    onClick = onMarkLearned,
                    enabled = !isLearned,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isLearned) learnedGreen else colors.neonCyan,
                        disabledContentColor = learnedGreen,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.sdp,
                        if (isLearned) learnedGreen.copy(alpha = 0.5f) else colors.neonCyan.copy(alpha = 0.5f),
                    ),
                ) {
                    Icon(
                        imageVector = if (isLearned) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.sdp),
                    )
                    Spacer(modifier = Modifier.width(6.sdp))
                    Text(
                        text = if (isLearned) "Learned — saved to review" else "Mark as Learned",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
