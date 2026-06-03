package com.speakmind.app.feature.vocabgroup.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.vocabgroup.domain.model.VocabGroupWord
import com.speakmind.app.navigation.GroupDetailDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.groupDetailScreen() {
    animatedComposable<GroupDetailDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<GroupDetailDestination>()
        val viewModel = koinViewModel<GroupDetailViewModel> {
            parametersOf(dest.groupId, dest.groupName)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val inputText by viewModel.inputText.collectAsStateWithLifecycle()
        GroupDetailContent(
            uiState = uiState,
            inputText = inputText,
            onInputChange = viewModel::onInputChange,
            onShowAddSheet = viewModel::onShowAddSheet,
            onDismissAddSheet = viewModel::onDismissAddSheet,
            onAddWord = viewModel::onAddWord,
            onToggleMic = viewModel::onToggleMic,
            onLookup = viewModel::onLookup,
            onToggleExpand = viewModel::onToggleExpand,
            onEditWord = viewModel::onEditWord,
            onDismissEdit = viewModel::onDismissEdit,
            onConfirmEdit = viewModel::onConfirmEdit,
            onDeleteWord = viewModel::onDeleteWord,
            onSpeakWord = viewModel::speakWord,
            onSpeakExample = viewModel::speakExample,
            onBack = viewModel::onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDetailContent(
    uiState: GroupDetailUiState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onShowAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onAddWord: () -> Unit,
    onToggleMic: () -> Unit,
    onLookup: (VocabGroupWord) -> Unit,
    onToggleExpand: (Long) -> Unit,
    onEditWord: (VocabGroupWord) -> Unit,
    onDismissEdit: () -> Unit,
    onConfirmEdit: (String) -> Unit,
    onDeleteWord: (Long) -> Unit,
    onSpeakWord: (String) -> Unit,
    onSpeakExample: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    uiState.editingWord?.let { editing ->
        EditWordDialog(
            currentWord = editing.word,
            onDismiss = onDismissEdit,
            onConfirm = onConfirmEdit,
        )
    }

    if (uiState.showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissAddSheet,
            sheetState = sheetState,
            containerColor = colors.surface,
            dragHandle = null,
        ) {
            AddWordSheetContent(
                inputText = inputText,
                isListening = uiState.isListening,
                onInputChange = onInputChange,
                onAddWord = onAddWord,
                onToggleMic = onToggleMic,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.sdp, bottom = 8.sdp)
                    .padding(horizontal = 4.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = uiState.groupName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            if (uiState.words.isEmpty()) {
                EmptyWordsState()
            } else {
                Text(
                    text = "${uiState.words.size} word${if (uiState.words.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.textMuted,
                        letterSpacing = 0.5.ssp,
                    ),
                    modifier = Modifier.padding(horizontal = 20.sdp, vertical = 4.sdp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 20.sdp,
                        end = 20.sdp,
                        top = 8.sdp,
                        bottom = 110.sdp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.sdp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(uiState.words, key = { it.id }) { word ->
                        WordCard(
                            word = word,
                            isExpanded = uiState.expandedWordId == word.id,
                            isLookingUp = word.id in uiState.loadingWordIds,
                            onToggleExpand = { onToggleExpand(word.id) },
                            onLookup = { onLookup(word) },
                            onEdit = { onEditWord(word) },
                            onDelete = { onDeleteWord(word.id) },
                            onSpeakWord = onSpeakWord,
                            onSpeakExample = onSpeakExample,
                        )
                    }
                }
            }
        }

        AddWordFab(
            onClick = onShowAddSheet,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.sdp, bottom = 36.sdp),
        )
    }
}

// ─────────────────────────────────────────────────────
// Word card — modern pill-chip action buttons
// ─────────────────────────────────────────────────────

@Composable
private fun WordCard(
    word: VocabGroupWord,
    isExpanded: Boolean,
    isLookingUp: Boolean,
    onToggleExpand: () -> Unit,
    onLookup: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSpeakWord: (String) -> Unit,
    onSpeakExample: (String) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val hasMeaning = word.meaning.isNotEmpty()
    val headerShape = if (isExpanded && hasMeaning)
        RoundedCornerShape(topStart = 16.sdp, topEnd = 16.sdp, bottomStart = 0.sdp, bottomEnd = 0.sdp)
    else
        RoundedCornerShape(16.sdp)

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .background(colors.surfaceVariant.copy(alpha = 0.75f))
                .border(
                    width = 1.sdp,
                    color = colors.magenta.copy(alpha = if (isExpanded) 0.4f else 0.2f),
                    shape = headerShape,
                )
                .clickable(enabled = hasMeaning, onClick = onToggleExpand)
                .padding(horizontal = 16.sdp, vertical = 14.sdp),
        ) {
            // Word + part of speech
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (hasMeaning && word.partOfSpeech.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.sdp))
                            .background(colors.magenta.copy(alpha = 0.12f))
                            .padding(horizontal = 8.sdp, vertical = 3.sdp),
                    ) {
                        Text(
                            text = word.partOfSpeech,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.magenta,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
                if (isLookingUp) {
                    Spacer(modifier = Modifier.width(8.sdp))
                    CircularProgressIndicator(
                        color = colors.magenta,
                        modifier = Modifier.size(16.sdp),
                        strokeWidth = 2.sdp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.sdp))

            // Action chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                if (!isLookingUp) {
                    ActionChip(
                        icon = Icons.Default.Search,
                        label = if (hasMeaning) "Re-look" else "Look up",
                        tint = colors.magenta,
                        onClick = onLookup,
                    )
                }
                ActionChip(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    tint = colors.neonCyan,
                    onClick = onEdit,
                )
                ActionChip(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    tint = colors.error,
                    onClick = onDelete,
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded && hasMeaning,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            WordDetailCard(
                word = word,
                onSpeakWord = onSpeakWord,
                onSpeakExample = onSpeakExample,
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.08f))
            .border(1.sdp, tint.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.sdp, vertical = 6.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.sdp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.sdp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = tint,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────
// Word detail card — meaning, examples, TTS buttons
// ─────────────────────────────────────────────────────

@Composable
private fun WordDetailCard(
    word: VocabGroupWord,
    onSpeakWord: (String) -> Unit,
    onSpeakExample: (String) -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.sdp, bottomEnd = 16.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.35f))
            .border(
                1.sdp,
                colors.neonCyan.copy(alpha = 0.1f),
                RoundedCornerShape(bottomStart = 16.sdp, bottomEnd = 16.sdp),
            )
            .padding(16.sdp),
        verticalArrangement = Arrangement.spacedBy(12.sdp),
    ) {
        // Phonetic + TTS row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (word.phonetic.isNotEmpty()) {
                Text(
                    text = word.phonetic,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.neonCyan.copy(alpha = 0.75f),
                        fontStyle = FontStyle.Italic,
                    ),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            // Listen button for the word
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.neonCyan.copy(alpha = 0.1f))
                    .clickable { onSpeakWord(word.word) }
                    .padding(horizontal = 10.sdp, vertical = 5.sdp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.sdp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Listen to word",
                    tint = colors.neonCyan,
                    modifier = Modifier.size(14.sdp),
                )
                Text(
                    text = "Listen",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.neonCyan,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        // Meaning
        Text(
            text = word.meaning,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.textSecondary,
                lineHeight = 22.ssp,
            ),
        )

        // Examples
        if (word.examples.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colors.surfaceVariant,
                )
                Text(
                    text = "  Examples  ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.textMuted,
                        letterSpacing = 0.5.ssp,
                    ),
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colors.surfaceVariant,
                )
            }

            word.examples.take(2).forEach { sentence ->
                ExampleRow(
                    sentence = sentence,
                    onSpeak = { onSpeakExample(sentence) },
                )
            }
        }
    }
}

@Composable
private fun ExampleRow(sentence: String, onSpeak: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.sdp)
                .heightIn(min = 48.sdp)
                .clip(RoundedCornerShape(topStart = 12.sdp, bottomStart = 12.sdp))
                .background(
                    Brush.verticalGradient(
                        listOf(colors.neonCyan.copy(alpha = 0.7f), colors.magenta.copy(alpha = 0.4f))
                    )
                ),
        )
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodySmall.copy(
                color = colors.textSecondary,
                fontStyle = FontStyle.Italic,
                lineHeight = 18.ssp,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.sdp, vertical = 12.sdp),
        )
        // TTS button for example
        Box(
            modifier = Modifier
                .size(36.sdp)
                .clip(CircleShape)
                .clickable(onClick = onSpeak),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Play example",
                tint = colors.neonCyan.copy(alpha = 0.6f),
                modifier = Modifier.size(16.sdp),
            )
        }
        Spacer(modifier = Modifier.width(4.sdp))
    }
}

// ─────────────────────────────────────────────────────
// Add word bottom sheet — elegant redesign
// ─────────────────────────────────────────────────────

@Composable
private fun AddWordSheetContent(
    inputText: String,
    isListening: Boolean,
    onInputChange: (String) -> Unit,
    onAddWord: () -> Unit,
    onToggleMic: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val focusRequester = remember { FocusRequester() }

    // Pulse animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micScale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Gradient accent bar at top of sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.sdp)
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.magenta, colors.neonCyan)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp)
                .padding(top = 24.sdp, bottom = 28.sdp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.sdp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.sdp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.sdp),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.magenta,
                        modifier = Modifier.size(18.sdp),
                    )
                    Text(
                        text = "Add a Word",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Text(
                    text = "Type it below or tap the mic to speak",
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
                )
            }

            // Large text input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.sdp))
                    .background(colors.surfaceVariant.copy(alpha = 0.8f))
                    .border(
                        width = 1.5.sdp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                colors.magenta.copy(alpha = if (isListening) 0.8f else 0.5f),
                                colors.neonCyan.copy(alpha = if (isListening) 0.6f else 0.3f),
                            )
                        ),
                        shape = RoundedCornerShape(18.sdp),
                    )
                    .padding(horizontal = 20.sdp, vertical = 18.sdp),
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = if (isListening) "Listening..." else "e.g. resilient, pursue...",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = if (isListening) colors.magenta.copy(alpha = 0.6f)
                                    else colors.textMuted.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Normal,
                        ),
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAddWord() }),
                    cursorBrush = SolidColor(colors.magenta),
                )
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.sdp),
            ) {
                // Mic / Speak button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.sdp))
                        .background(
                            if (isListening)
                                Brush.verticalGradient(
                                    listOf(
                                        colors.magenta.copy(alpha = glowAlpha + 0.2f),
                                        colors.magenta.copy(alpha = glowAlpha),
                                    )
                                )
                            else
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                )
                        )
                        .border(
                            width = 1.5.sdp,
                            color = colors.magenta.copy(alpha = if (isListening) 0.7f else 0.4f),
                            shape = RoundedCornerShape(14.sdp),
                        )
                        .clickable(onClick = onToggleMic)
                        .padding(vertical = 14.sdp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.sdp),
                        modifier = if (isListening) Modifier.scale(micScale) else Modifier,
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = colors.magenta,
                            modifier = Modifier.size(18.sdp),
                        )
                        Text(
                            text = if (isListening) "Stop" else "Speak",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = colors.magenta,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                // Add word button
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .clip(RoundedCornerShape(14.sdp))
                        .background(
                            if (inputText.isNotBlank())
                                Brush.horizontalGradient(
                                    listOf(colors.magenta, colors.magenta.copy(alpha = 0.75f))
                                )
                            else
                                Brush.horizontalGradient(
                                    listOf(
                                        colors.surfaceVariant.copy(alpha = 0.6f),
                                        colors.surfaceVariant.copy(alpha = 0.6f),
                                    )
                                )
                        )
                        .clickable(enabled = inputText.isNotBlank(), onClick = onAddWord)
                        .padding(vertical = 14.sdp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.sdp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = if (inputText.isNotBlank()) Color.White else colors.textMuted,
                            modifier = Modifier.size(18.sdp),
                        )
                        Text(
                            text = "Add Word",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (inputText.isNotBlank()) Color.White else colors.textMuted,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// FAB
// ─────────────────────────────────────────────────────

@Composable
private fun AddWordFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current

    val infiniteTransition = rememberInfiniteTransition(label = "fab_float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Box(
        modifier = modifier.offset(y = floatY.sdp),
        contentAlignment = Alignment.Center,
    ) {
        // Glow halo
        Box(
            modifier = Modifier
                .size(66.sdp)
                .background(
                    Brush.radialGradient(
                        listOf(colors.magenta.copy(alpha = glowAlpha), Color.Transparent)
                    ),
                    CircleShape,
                )
        )
        // Button
        Box(
            modifier = Modifier
                .size(52.sdp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(colors.magenta, colors.magenta.copy(alpha = 0.75f))
                    )
                )
                .border(
                    width = 1.5.sdp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                    ),
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add word",
                tint = Color.White,
                modifier = Modifier.size(26.sdp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────

@Composable
private fun EmptyWordsState() {
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
            Text(text = "📝", fontSize = 48.ssp)
            Text(
                text = "No words yet",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.textPrimary, fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap the + button to add your first word.\nType it or say it out loud.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary,
                    lineHeight = 22.ssp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────
// Edit word dialog
// ─────────────────────────────────────────────────────

@Composable
private fun EditWordDialog(
    currentWord: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var editText by remember { mutableStateOf(currentWord) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                "Edit Word",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.textPrimary, fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.sdp))
                    .background(colors.surfaceVariant.copy(alpha = 0.8f))
                    .border(1.5.sdp, colors.magenta.copy(alpha = 0.4f), RoundedCornerShape(12.sdp))
                    .padding(horizontal = 14.sdp, vertical = 14.sdp),
            ) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.magenta),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(editText) },
                enabled = editText.isNotBlank(),
            ) {
                Text(
                    "Save",
                    color = if (editText.isNotBlank()) colors.magenta else colors.textMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textMuted)
            }
        },
    )
}
