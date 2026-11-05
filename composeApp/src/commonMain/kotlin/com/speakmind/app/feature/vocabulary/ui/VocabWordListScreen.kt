package com.speakmind.app.feature.vocabulary.ui

import com.speakmind.app.feature.vocabulary.domain.cleanExamples

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.vocabulary.domain.model.VocabWord
import com.speakmind.app.navigation.VocabWordListDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyFitText
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyLevelTone
import com.speakmind.app.ui.components.SpeakySpeedButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.components.speakyLevelTone
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
    val tone = speakyLevelTone(uiState.level)

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
            Column(Modifier.weight(1f)) {
                Text(
                    if (uiState.levelLabel.isNotEmpty()) "${uiState.level} · ${uiState.levelLabel}" else uiState.level,
                    style = displayStyle(22.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                if (!uiState.isLoading) {
                    val learned = uiState.words.count { it.word in uiState.learnedWords }
                    Text("$learned of ${uiState.words.size} learned", color = colors.textMuted, fontSize = 12.sp)
                }
            }
            SpeakySpeedButton()
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(uiState.words) { index, word ->
                    WordItem(
                        word = word,
                        isExpanded = uiState.expandedWordIndex == index,
                        isLearned = word.word in uiState.learnedWords,
                        tone = tone,
                        onClick = { onWordClick(index) },
                        onSpeak = onSpeak,
                        onMarkLearned = { onMarkLearned(word) },
                    )
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun WordItem(
    word: VocabWord,
    isExpanded: Boolean,
    isLearned: Boolean,
    tone: SpeakyLevelTone,
    onClick: () -> Unit,
    onSpeak: (String) -> Unit,
    onMarkLearned: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    if (!isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(word.word, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (word.partOfSpeech.isNotEmpty()) Text(word.partOfSpeech, color = colors.textMuted, fontSize = 12.sp)
            }
            if (isLearned) {
                Text(
                    "Learned",
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.mintSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = colors.onMintSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            SpeakyIconButton(
                icon = SpeakyIcons.Speaker,
                contentDescription = "Play word",
                onClick = { onSpeak(word.word) },
                iconSize = 18.dp,
                background = colors.backgroundDark,
                tint = colors.textSecondary,
            )
        }
        return
    }

    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(colors.surface)
            .border(2.dp, tone.accent, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                SpeakyFitText(word.word, style = displayStyle(24.sp), color = colors.neonCyan, modifier = Modifier.fillMaxWidth())
                if (word.partOfSpeech.isNotEmpty()) Text(word.partOfSpeech, color = colors.textMuted, fontSize = 12.sp)
            }
            SpeakyIconButton(
                icon = SpeakyIcons.Speaker,
                contentDescription = "Play word",
                onClick = { onSpeak(word.word) },
                background = tone.soft,
                tint = tone.onSoft,
            )
        }
        Text(word.meaning, color = colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
        // A few short examples keep "Mark as learned" in view.
        val examples = cleanExamples(word.sentences, MAX_SENTENCES)
        if (examples.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                examples.forEach { sentence ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.backgroundDark)
                            .clickable { onSpeak(sentence) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(sentence, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(SpeakyIcons.Speaker, contentDescription = "Play sentence", tint = colors.faint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isLearned) colors.mintSoft else LearnedGreen)
                .clickable(enabled = !isLearned, role = Role.Button, onClick = onMarkLearned),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (isLearned) colors.onMintSoft else Color.White
            Icon(SpeakyIcons.Check, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(
                if (isLearned) "Learned · saved to review" else "Mark as learned",
                color = tint,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

private val LearnedGreen = Color(0xFF19B88A)

private const val MAX_SENTENCES = 5
