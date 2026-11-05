package com.speakmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.ui.theme.LocalSpeakMindColors

enum class WordAction { SAVE, MEANING, TRANSLATE }

private val TABS = listOf(WordAction.TRANSLATE to "Translate", WordAction.MEANING to "Meaning", WordAction.SAVE to "Save")

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
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp).size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp)).background(colors.faint.copy(alpha = 0.35f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeakyFitText(word, style = displayStyle(30.sp), color = colors.neonCyan, modifier = Modifier.weight(1f))
                SpeakyIconButton(
                    icon = SpeakyIcons.Speaker,
                    contentDescription = "Pronounce",
                    onClick = onSpeakWord,
                    size = 48.dp,
                    background = colors.primarySoft,
                    tint = colors.neonCyan,
                )
            }

            SpeakySegmentedTabs(
                options = TABS.map { it.second },
                selectedIndex = TABS.indexOfFirst { it.first == selectedAction },
                onSelect = { onActionSelected(TABS[it].first) },
                height = 40.dp,
                radius = 14.dp,
            )

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoadingAction -> CircularProgressIndicator(color = colors.neonCyan, modifier = Modifier.size(32.dp))
                    selectedAction == WordAction.SAVE -> WordSaveContent(wordSaved = wordSaved, onSave = onSaveWord)
                    selectedAction == WordAction.MEANING -> WordMeaningContent(meaningText, partOfSpeech)
                    selectedAction == WordAction.TRANSLATE -> WordTranslateContent(word, meaningText, translationText)
                }
            }
        }
    }
}

@Composable
private fun WordSaveContent(wordSaved: Boolean, onSave: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    if (wordSaved) {
        SpeakyPrimaryButton(
            text = "Saved to flashcards",
            onClick = {},
            leading = SpeakyIcons.Check,
            background = colors.mintSoft,
            color = colors.onMintSoft,
            height = 54.dp,
            radius = 16.dp,
        )
    } else {
        SpeakyPrimaryButton(
            text = "Save to flashcards",
            onClick = onSave,
            leading = SpeakyIcons.Bookmark,
            height = 54.dp,
            radius = 16.dp,
        )
    }
}

@Composable
private fun WordMeaningContent(meaningText: String?, partOfSpeech: String?) {
    val colors = LocalSpeakMindColors.current
    if (meaningText == null) {
        Unavailable("Meaning not found")
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (partOfSpeech != null) {
            SpeakyPill(
                partOfSpeech,
                background = colors.primarySoft,
                color = colors.onPrimarySoft,
                fontSize = 12.sp,
                padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(meaningText, color = colors.textPrimary, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun WordTranslateContent(word: String, meaningText: String?, translationText: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EnglishArabicMeaning(
            english = meaningText ?: word,
            englishTitle = if (meaningText != null) word else null,
            arabicWord = translationText,
            framed = true,
        )
        if (translationText == null) Unavailable("Arabic translation not available")
    }
}

@Composable
private fun Unavailable(text: String) {
    Text(
        text,
        color = LocalSpeakMindColors.current.textMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
