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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import com.speakmind.app.navigation.WordLookupDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.TtsSpeedButton
import com.speakmind.app.ui.components.TtsSpeedButtonStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.levelColorOf
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
            .background(colors.backgroundGradient)
            .imePadding(), // push content above keyboard
    ) {
        // ── Search bar (single row: back + field + clear) ─────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 52.sdp, bottom = 12.sdp)
                .padding(horizontal = 8.sdp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.sdp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.sdp))
                    .background(colors.surfaceVariant.copy(alpha = 0.85f))
                    .border(
                        width = 1.5.sdp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                colors.neonCyan.copy(alpha = 0.55f),
                                colors.magenta.copy(alpha = 0.35f),
                            )
                        ),
                        shape = RoundedCornerShape(20.sdp),
                    )
                    .padding(horizontal = 14.sdp, vertical = 11.sdp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.sdp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.neonCyan,
                    modifier = Modifier.size(20.sdp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Try: resilient, pursue...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textMuted,
                            ),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.textPrimary,
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
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(18.sdp)
                            .clickable { query = "" },
                    )
                }
            }
        }

        when (uiState) {
            is WordLookupUiState.Idle -> IdleHint()
            is WordLookupUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
            is WordLookupUiState.Result -> ResultContent(
                result = uiState.result,
                isSaved = uiState.isSaved,
                onSpeakWord = onSpeakWord,
                onSpeakSentence = onSpeakSentence,
                onSaveWord = onSaveWord,
            )
            WordLookupUiState.NotFound -> NotFoundContent()
        }
    }
}

@Composable
private fun IdleHint() {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.sdp),
            modifier = Modifier.padding(horizontal = 40.sdp),
        ) {
            Text(text = "📖", fontSize = 52.ssp)
            Spacer(modifier = Modifier.height(4.sdp))
            Text(
                text = "Look up any word",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Get the meaning, pronunciation,\nand example sentences instantly.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary,
                    lineHeight = 22.ssp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultContent(
    result: WordLookupResult,
    isSaved: Boolean,
    onSpeakWord: (String) -> Unit,
    onSpeakSentence: (String) -> Unit,
    onSaveWord: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.sdp,
            end = 20.sdp,
            top = 8.sdp,
            bottom = 32.sdp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.sdp),
    ) {
        // ── Word card ──────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.sdp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        1.sdp,
                        colors.neonCyan.copy(alpha = 0.15f),
                        RoundedCornerShape(20.sdp),
                    )
                    .padding(20.sdp),
                verticalArrangement = Arrangement.spacedBy(12.sdp),
            ) {
                // Word + level badge + optional AI sparkle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.sdp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = result.word,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = colors.neonCyan,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (!result.level.isNullOrEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.sdp))
                                .background(levelColorOf(result.level).copy(alpha = 0.2f))
                                .border(
                                    1.sdp,
                                    levelColorOf(result.level).copy(alpha = 0.4f),
                                    RoundedCornerShape(8.sdp),
                                )
                                .padding(horizontal = 10.sdp, vertical = 4.sdp),
                        ) {
                            Text(
                                text = result.level,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = levelColorOf(result.level),
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }

                // Phonetic · part of speech · Listen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.sdp),
                ) {
                    if (result.phonetic.isNotEmpty()) {
                        Text(
                            text = result.phonetic,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.neonCyan.copy(alpha = 0.6f),
                                fontStyle = FontStyle.Italic,
                            ),
                        )
                    }
                    if (result.phonetic.isNotEmpty() && result.partOfSpeech.isNotEmpty()) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textMuted,
                            ),
                        )
                    }
                    if (result.partOfSpeech.isNotEmpty()) {
                        Text(
                            text = result.partOfSpeech,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textMuted,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // Listen + Speed chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.sdp))
                                .background(colors.neonCyan.copy(alpha = 0.12f))
                                .clickable { onSpeakWord(result.word) }
                                .padding(horizontal = 12.sdp, vertical = 6.sdp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.sdp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Listen",
                                tint = colors.neonCyan,
                                modifier = Modifier.size(16.sdp),
                            )
                            Text(
                                text = "Listen",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = colors.neonCyan,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                        TtsSpeedButton(style = TtsSpeedButtonStyle.Pill)
                    }
                }

                HorizontalDivider(
                    color = colors.surfaceVariant,
                    modifier = Modifier.padding(vertical = 4.sdp),
                )

                // Meaning
                Text(
                    text = result.meaning,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.textSecondary,
                        lineHeight = 26.ssp,
                    ),
                )

                HorizontalDivider(
                    color = colors.surfaceVariant,
                    modifier = Modifier.padding(vertical = 4.sdp),
                )

                // Save to My Words button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.sdp))
                        .background(colors.neonCyan.copy(alpha = if (isSaved) 0.15f else 0.08f))
                        .border(
                            width = if (isSaved) 1.5.sdp else 1.sdp,
                            color = colors.neonCyan.copy(alpha = if (isSaved) 0.5f else 0.4f),
                            shape = RoundedCornerShape(12.sdp),
                        )
                        .clickable(enabled = !isSaved, onClick = onSaveWord)
                        .padding(horizontal = 16.sdp, vertical = 12.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.sdp),
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = colors.neonCyan,
                        modifier = Modifier.size(18.sdp),
                    )
                    Text(
                        text = if (isSaved) "Saved to My Words" else "Save to My Words",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.neonCyan,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }

        // ── Examples ───────────────────────────────────────────
        if (result.examples.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.sdp))
                Text(
                    text = "Examples",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = colors.textMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.ssp,
                    ),
                    modifier = Modifier.padding(bottom = 10.sdp),
                )
            }
            itemsIndexed(result.examples) { index, sentence ->
                val isLast = index == result.examples.lastIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isLast) 0.sdp else 10.sdp)
                        .clip(RoundedCornerShape(14.sdp))
                        .background(colors.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            1.sdp,
                            colors.neonCyan.copy(alpha = 0.08f),
                            RoundedCornerShape(14.sdp),
                        )
                        .padding(start = 0.sdp, end = 4.sdp, top = 0.sdp, bottom = 0.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left accent bar
                    Box(
                        modifier = Modifier
                            .width(3.sdp)
                            .height(52.sdp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 14.sdp,
                                    bottomStart = 14.sdp,
                                )
                            )
                            .background(colors.neonCyan.copy(alpha = 0.4f)),
                    )
                    Spacer(modifier = Modifier.width(12.sdp))
                    Text(
                        text = sentence,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.textSecondary,
                            lineHeight = 22.ssp,
                            fontStyle = FontStyle.Italic,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.sdp),
                    )
                    IconButton(
                        onClick = { onSpeakSentence(sentence) },
                        modifier = Modifier.size(40.sdp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Play",
                            tint = colors.neonCyan.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.sdp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotFoundContent() {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.sdp),
            modifier = Modifier.padding(horizontal = 40.sdp),
        ) {
            Text(text = "😕", fontSize = 52.ssp)
            Text(
                text = "Word not found",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Couldn't find this word in the dictionary.\nCheck the spelling and try again.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary,
                    lineHeight = 22.ssp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}
