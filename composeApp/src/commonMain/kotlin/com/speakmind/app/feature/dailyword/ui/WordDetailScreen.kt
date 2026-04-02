package com.speakmind.app.feature.dailyword.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.ui.theme.levelColorOf
import com.speakmind.app.navigation.WordDetailDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.TtsSpeedButton
import com.speakmind.app.ui.components.TtsSpeedButtonStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.wordDetailScreen() {
    animatedComposable<WordDetailDestination> { backStackEntry ->
        val wordId = backStackEntry.arguments?.getLong("wordId") ?: -1L
        val wordString = backStackEntry.arguments?.getString("word") ?: ""
        val viewModel = koinViewModel<WordDetailViewModel> {
            parametersOf(wordId, wordString)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        WordDetailContent(
            uiState = uiState,
            onBack = viewModel::onBack,
            onSpeakWord = viewModel::speakWord,
            onSpeakSentence = viewModel::speakSentence,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordDetailContent(
    uiState: WordDetailUiState,
    onBack: () -> Unit,
    onSpeakWord: () -> Unit,
    onSpeakSentence: (String) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val word = uiState.word

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        // Top bar
        TopAppBar(
            title = { Text("Word Detail", color = colors.textPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
        } else if (word == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Word not found", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 16.sdp),
                verticalArrangement = Arrangement.spacedBy(16.sdp),
            ) {
                // Word + level badge
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.sdp),
                    ) {
                        Text(
                            text = word.word,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = colors.neonCyan,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.sdp))
                                .background(levelColorOf(word.level).copy(alpha = 0.2f))
                                .padding(horizontal = 10.sdp, vertical = 4.sdp)
                        ) {
                            Text(
                                text = word.level,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = levelColorOf(word.level),
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }

                // Part of speech
                if (word.partOfSpeech.isNotEmpty()) {
                    item {
                        Text(
                            text = word.partOfSpeech,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.textMuted,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }

                // Listen button + speed chip
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.sdp))
                                .background(colors.neonCyan.copy(alpha = 0.1f))
                                .border(1.sdp, colors.neonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.sdp))
                                .clickable(onClick = onSpeakWord)
                                .padding(horizontal = 16.sdp, vertical = 10.sdp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.sdp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Listen",
                                tint = colors.neonCyan,
                                modifier = Modifier.size(20.sdp),
                            )
                            Text(
                                text = "Listen",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = colors.neonCyan,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                        TtsSpeedButton(style = TtsSpeedButtonStyle.Chip)
                    }
                }

                // Meaning
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                        Text(
                            text = "Meaning",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = word.meaning,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.textSecondary,
                                lineHeight = 24.ssp,
                            ),
                        )
                    }
                }

                // Divider
                item {
                    HorizontalDivider(color = colors.surfaceVariant)
                }

                // Example sentences
                if (word.sentences.isNotEmpty()) {
                    item {
                        Text(
                            text = "Examples",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    itemsIndexed(word.sentences) { _, sentence ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.sdp))
                                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.sdp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sentence,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.textSecondary,
                                    lineHeight = 22.ssp,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.sdp))
                            IconButton(
                                onClick = { onSpeakSentence(sentence) },
                                modifier = Modifier.size(36.sdp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Play",
                                    tint = colors.neonCyan,
                                    modifier = Modifier.size(20.sdp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
