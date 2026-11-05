package com.speakmind.app.feature.dailyword.ui

import com.speakmind.app.feature.vocabulary.domain.cleanExamples

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.WordDetailDestination
import com.speakmind.app.ui.components.EnglishArabicMeaning
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyFitText
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakySectionLabel
import com.speakmind.app.ui.components.SpeakySpeedButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
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
            .background(colors.backgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SpeakyBackButton(onClick = onBack, modifier = Modifier.padding(start = 20.dp, top = 20.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
        } else if (word == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Word not found", color = colors.textSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    WordHero(
                        word = word.word,
                        level = word.level,
                        partOfSpeech = word.partOfSpeech,
                        onListen = onSpeakWord,
                    )
                }
                item {
                    EnglishArabicMeaning(
                        english = word.meaning,
                        arabicWord = uiState.arabic,
                    )
                }

                val examples = cleanExamples(word.sentences, MAX_EXAMPLES)
                if (examples.isNotEmpty()) {
                    item { SpeakySectionLabel("Examples", Modifier.padding(start = 4.dp)) }
                    items(examples) { sentence ->
                        ExampleRow(sentence = sentence, onSpeak = { onSpeakSentence(sentence) })
                    }
                }
            }
        }
    }
}

/** The violet card: level / part-of-speech pills, the word, Listen + speed. */
@Composable
private fun WordHero(word: String, level: String, partOfSpeech: String, onListen: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    // Dark mode's brand tone is a pale lilac; white text needs the deeper violet there.
    // The Premium themes use their own brand colour with its matching text colour.
    val heroColor = when {
        colors.isGold -> colors.neonCyan
        colors.surface.luminance() < 0.5f -> colors.neonCyanDark
        else -> colors.neonCyan
    }
    val onHero = if (colors.isGold) colors.onPrimary else Color.White
    val onHeroDeep = when {
        !colors.isGold -> Color(0xFF2E1E9E)
        colors.isDark -> colors.onPrimary
        else -> colors.neonCyanDark
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroColor)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Words saved from Word Lookup have no level: no empty pill.
        if (level.isNotBlank() || partOfSpeech.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(level, partOfSpeech).filter { it.isNotBlank() }.forEach {
                    Text(
                        it,
                        color = onHero,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(onHero.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        SpeakyFitText(word, style = displayStyle(44.sp, lineHeight = 46.sp), color = onHero, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable(role = Role.Button, onClick = onListen)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(SpeakyIcons.Speaker, contentDescription = null, tint = onHeroDeep, modifier = Modifier.size(18.dp))
                Text("Listen", color = onHeroDeep, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            SpeakySpeedButton(
                background = Color.Transparent,
                color = onHero,
                border = onHero.copy(alpha = 0.5f),
                height = 46.dp,
                radius = 14.dp,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ExampleRow(sentence: String, onSpeak: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            sentence,
            color = colors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
        SpeakyIconButton(
            icon = SpeakyIcons.Speaker,
            contentDescription = "Listen to example",
            onClick = onSpeak,
            iconSize = 18.dp,
            background = colors.primarySoft,
            tint = colors.neonCyan,
        )
    }
}

private const val MAX_EXAMPLES = 8
