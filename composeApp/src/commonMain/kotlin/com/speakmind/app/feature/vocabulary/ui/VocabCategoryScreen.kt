package com.speakmind.app.feature.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import network.chaintech.sdpcomposemultiplatform.sdp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.ui.theme.levelColorOf
import com.speakmind.app.navigation.VocabCategoryDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.vocabCategoryScreen() {
    animatedComposable<VocabCategoryDestination> {
        val viewModel = koinViewModel<VocabCategoryViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        VocabCategoryContent(
            uiState = uiState,
            onLevelClick = viewModel::onLevelClicked,
            onGoBack = viewModel::onGoBack,
        )
    }
}

@Composable
private fun VocabCategoryContent(
    uiState: VocabCategoryUiState,
    onLevelClick: (String) -> Unit,
    onGoBack: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onGoBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "Flash Cards",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
            } else {
                // Subtitle
                Text(
                    text = "Choose a level to start learning vocabulary",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary,
                    ),
                    modifier = Modifier.padding(horizontal = 20.sdp, vertical = 8.sdp),
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 12.sdp),
                    verticalArrangement = Arrangement.spacedBy(14.sdp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(uiState.levels) { level ->
                        LevelCard(
                            summary = level,
                            onClick = { onLevelClick(level.level) },
                        )
                    }
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LevelCard(summary: VocabLevelSummary, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val levelColor = levelColorOf(summary.level)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = 1.sdp,
                color = levelColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.sdp),
            )
            .clickable(onClick = onClick)
            .padding(16.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Level badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.sdp)
                .clip(RoundedCornerShape(12.sdp))
                .background(levelColor.copy(alpha = 0.2f)),
        ) {
            Text(
                text = summary.level,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = Modifier.width(16.sdp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(2.sdp))
            Text(
                text = summary.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary,
                ),
            )
            Spacer(modifier = Modifier.height(4.sdp))
            Text(
                text = "${summary.wordCount} words",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = levelColor,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textMuted,
        )
    }
}
