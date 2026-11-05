package com.speakmind.app.feature.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.VocabCategoryDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyProgress
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.components.speakyLevelTone
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeakyBackButton(onClick = onGoBack)
            Text("Flash cards", style = displayStyle(26.sp), color = colors.textPrimary)
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Text(
                        if (uiState.userLevel != null) "Choose a level. Your level is highlighted."
                        else "Choose a level to start learning vocabulary.",
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                    )
                }
                items(uiState.levels) { level ->
                    LevelCard(
                        summary = level,
                        isMine = level.level == uiState.userLevel,
                        onClick = { onLevelClick(level.level) },
                    )
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun LevelCard(summary: VocabLevelSummary, isMine: Boolean, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val tone = speakyLevelTone(summary.level)
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .then(if (isMine) Modifier.border(2.dp, tone.accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tone.soft),
        ) {
            Text(summary.level, color = tone.onSoft, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(summary.label, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(summary.description, color = colors.textMuted, fontSize = 13.sp)
            // A sliver of colour even at 0 so the bar reads as a progress bar.
            val progress = if (summary.wordCount > 0) summary.learnedCount.toFloat() / summary.wordCount else 0f
            SpeakyProgress(progress.coerceAtLeast(0.02f), tone.accent, Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${summary.wordCount}", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("words", color = colors.textMuted, fontSize = 11.sp)
        }
    }
}
