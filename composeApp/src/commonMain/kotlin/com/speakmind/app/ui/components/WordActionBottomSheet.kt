package com.speakmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import com.speakmind.app.ui.theme.LocalSpeakMindColors

enum class WordAction { SAVE, MEANING, TRANSLATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordActionBottomSheet(
    word: String,
    wordSaved: Boolean,
    selectedAction: WordAction,
    meaningText: String?,
    partOfSpeech: String?,
    translationText: String?,
    isLoadingAction: Boolean,
    onActionSelected: (WordAction) -> Unit,
    onSaveWord: () -> Unit,
    onSpeakWord: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.textMuted.copy(alpha = 0.4f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp)
                .padding(bottom = 40.sdp),
            verticalArrangement = Arrangement.spacedBy(16.sdp),
        ) {
            // Word + pronounce button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = colors.neonCyan,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSpeakWord) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Pronounce",
                        tint = colors.neonCyan,
                        modifier = Modifier.size(26.sdp),
                    )
                }
            }

            // Action chips row
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                WordActionChip(
                    label = "Save",
                    icon = if (wordSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    selected = selectedAction == WordAction.SAVE,
                    onClick = { onActionSelected(WordAction.SAVE) },
                )
                WordActionChip(
                    label = "Meaning",
                    icon = Icons.Outlined.MenuBook,
                    selected = selectedAction == WordAction.MEANING,
                    onClick = { onActionSelected(WordAction.MEANING) },
                )
                WordActionChip(
                    label = "Translate",
                    icon = Icons.Outlined.Language,
                    selected = selectedAction == WordAction.TRANSLATE,
                    onClick = { onActionSelected(WordAction.TRANSLATE) },
                )
            }

            HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))

            // Content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.sdp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoadingAction -> CircularProgressIndicator(
                        color = colors.neonCyan,
                        modifier = Modifier.size(32.sdp),
                    )
                    selectedAction == WordAction.SAVE -> WordSaveContent(
                        wordSaved = wordSaved,
                        onSave = onSaveWord,
                    )
                    selectedAction == WordAction.MEANING -> WordMeaningContent(
                        meaningText = meaningText,
                        partOfSpeech = partOfSpeech,
                    )
                    selectedAction == WordAction.TRANSLATE -> WordTranslateContent(
                        word = word,
                        translationText = translationText,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordActionChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.sdp),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.neonCyan,
            selectedLabelColor = colors.backgroundDark,
            selectedLeadingIconColor = colors.backgroundDark,
            containerColor = colors.surfaceVariant.copy(alpha = 0.3f),
            labelColor = colors.textMuted,
            iconColor = colors.textMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = colors.neonCyan,
            borderColor = colors.surfaceVariant,
        ),
    )
}

@Composable
private fun WordSaveContent(
    wordSaved: Boolean,
    onSave: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    if (wordSaved) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.neonCyan,
                modifier = Modifier.size(28.sdp),
            )
            Text(
                text = "Saved to flashcards!",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.neonCyan,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.sdp),
        ) {
            Text(
                text = "Save this word to your flashcard deck to review it later.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary,
                    lineHeight = 22.ssp,
                ),
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.sdp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.neonCyan,
                    contentColor = colors.backgroundDark,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.sdp),
                )
                Spacer(modifier = Modifier.width(8.sdp))
                Text(
                    text = "Save to Flashcards",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun WordMeaningContent(
    meaningText: String?,
    partOfSpeech: String?,
) {
    val colors = LocalSpeakMindColors.current
    if (meaningText == null) {
        Text(
            text = "Meaning not found",
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.sdp),
        ) {
            if (partOfSpeech != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.sdp))
                        .background(colors.neonCyan.copy(alpha = 0.12f))
                        .padding(horizontal = 8.sdp, vertical = 3.sdp),
                ) {
                    Text(
                        text = partOfSpeech,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.neonCyan,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            Text(
                text = meaningText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.textPrimary,
                    lineHeight = 26.ssp,
                ),
            )
        }
    }
}

@Composable
private fun WordTranslateContent(
    word: String,
    translationText: String?,
) {
    val colors = LocalSpeakMindColors.current
    if (translationText == null) {
        Text(
            text = "Translation not available",
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.sdp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "English",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                )
                Text(
                    text = "العربية",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                )
            }
            HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = translationText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = colors.neonCyan,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
