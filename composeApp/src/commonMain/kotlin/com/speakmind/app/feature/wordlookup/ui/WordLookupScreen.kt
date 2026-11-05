package com.speakmind.app.feature.wordlookup.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import com.speakmind.app.navigation.WordLookupDestination
import com.speakmind.app.ui.components.EnglishArabicMeaning
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyFitText
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPill
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.SpeakySectionLabel
import com.speakmind.app.ui.components.SpeakySpeedButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.wordLookupScreen() {
    animatedComposable<WordLookupDestination> {
        val viewModel = koinViewModel<WordLookupViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        WordLookupContent(
            uiState = uiState,
            onSearch = viewModel::lookUp,
            onBack = viewModel::onBack,
            onSpeakWord = viewModel::speakWord,
            onSpeakSentence = viewModel::speakSentence,
            onSaveWord = viewModel::saveWord,
        )
    }
}

@Composable
private fun WordLookupContent(
    uiState: WordLookupUiState,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onSpeakWord: (String) -> Unit,
    onSpeakSentence: (String) -> Unit,
    onSaveWord: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(), // push content above keyboard
    ) {
        // ── Back + search field ─────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeakyBackButton(onClick = onBack)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(2.dp, colors.neonCyan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(SpeakyIcons.Search, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Try: resilient, in the long run...",
                            color = colors.faint,
                            fontSize = 16.sp,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        cursorBrush = SolidColor(colors.neonCyan),
                    )
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Icon(
                        SpeakyIcons.Close,
                        contentDescription = "Clear",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { query = "" }
                            .padding(5.dp),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (uiState) {
                is WordLookupUiState.Idle -> MessageState(
                    icon = SpeakyIcons.Search,
                    title = "Look up any word or phrase",
                    body = "Get the meaning, Arabic translation and example sentences instantly. Phrases and sentences work too.",
                )
                is WordLookupUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
                is WordLookupUiState.Result -> ResultContent(
                    result = uiState.result,
                    onSpeakWord = onSpeakWord,
                    onSpeakSentence = onSpeakSentence,
                )
                WordLookupUiState.NotFound -> MessageState(
                    icon = SpeakyIcons.Close,
                    title = "Word not found",
                    body = "Couldn't find this word in the dictionary. Check the spelling and try again.",
                )
            }
        }

        // Save only real words with a meaning, not phrase-only translations.
        if (uiState is WordLookupUiState.Result && uiState.result.source != WordLookupResult.Source.TRANSLATION) {
            val saved = uiState.isSaved
            SpeakyPrimaryButton(
                text = if (saved) "Saved to my words" else "Save to my words",
                onClick = onSaveWord,
                // Stays "enabled" so the saved colours aren't dimmed; saveWord() ignores repeats.
                leading = if (saved) SpeakyIcons.Check else SpeakyIcons.Bookmark,
                background = if (saved) colors.mintSoft else colors.neonCyan,
                color = if (saved) colors.onMintSoft else colors.onPrimary,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun MessageState(icon: ImageVector, title: String, body: String) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(36.dp))
        }
        Text(title, style = displayStyle(22.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            body,
            color = colors.textSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultContent(
    result: WordLookupResult,
    onSpeakWord: (String) -> Unit,
    onSpeakSentence: (String) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val isTranslationOnly = result.source == WordLookupResult.Source.TRANSLATION

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Word / phrase card ─────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SpeakyFitText(
                            result.word,
                            style = displayStyle(if (isTranslationOnly) 26.sp else 36.sp),
                            color = colors.neonCyan,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val subtitle = listOf(result.phonetic, result.partOfSpeech).filter { it.isNotEmpty() }.joinToString(" · ")
                        if (subtitle.isNotEmpty() || !result.level.isNullOrEmpty() || isTranslationOnly) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (subtitle.isNotEmpty()) Text(subtitle, color = colors.textMuted, fontSize = 14.sp)
                                if (!result.level.isNullOrEmpty()) {
                                    SpeakyPill(
                                        result.level,
                                        background = colors.goldSoft,
                                        color = colors.onGoldSoft,
                                        fontSize = 12.sp,
                                        padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                if (isTranslationOnly) {
                                    SpeakyPill(
                                        "Phrase",
                                        background = colors.coralSoft,
                                        color = colors.onCoralSoft,
                                        fontSize = 12.sp,
                                        padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SpeakyIconButton(
                            icon = SpeakyIcons.Speaker,
                            contentDescription = "Listen",
                            onClick = { onSpeakWord(result.word) },
                            size = 52.dp,
                            iconSize = 22.dp,
                            background = colors.primarySoft,
                            tint = colors.neonCyan,
                        )
                        SpeakySpeedButton(background = colors.backgroundDark, height = 30.dp, radius = 10.dp, fontSize = 12.sp)
                    }
                }

                EnglishArabicMeaning(
                    english = result.meaning.takeIf { it.isNotBlank() },
                    arabicWord = result.arabicTranslation,
                    framed = true,
                )
            }
        }

        // ── Examples ───────────────────────────────────────────
        if (result.examples.isNotEmpty()) {
            item { SpeakySectionLabel("Examples", Modifier.padding(start = 4.dp)) }
            items(result.examples) { sentence ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .clickable { onSpeakSentence(sentence) }
                        .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
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
                    Icon(
                        SpeakyIcons.Speaker,
                        contentDescription = "Play",
                        tint = colors.faint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
