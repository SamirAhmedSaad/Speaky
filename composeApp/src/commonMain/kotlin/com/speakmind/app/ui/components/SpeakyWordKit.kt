package com.speakmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.TtsSpeedManager
import org.koin.compose.koinInject

// Pieces shared by the word screens (word detail, lookup, flash cards, review, word sheet).

/** "1×" listening-speed button in the redesign's shapes; opens the same picker as [TtsSpeedButton]. */
@Composable
fun SpeakySpeedButton(
    modifier: Modifier = Modifier,
    background: Color = LocalSpeakMindColors.current.surface,
    color: Color = LocalSpeakMindColors.current.textPrimary,
    border: Color? = null,
    height: Dp = 40.dp,
    radius: Dp = 12.dp,
    fontSize: TextUnit = 13.sp,
    ttsSpeedManager: TtsSpeedManager = koinInject(),
) {
    val speed by ttsSpeedManager.speed.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        TtsSpeedPickerDialog(
            currentSpeed = speed,
            onSpeedSelected = { ttsSpeedManager.setSpeed(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .height(height)
            .widthIn(min = 44.dp)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
            .clickable(role = Role.Button) { showPicker = true }
            .semantics { contentDescription = "Listening speed" }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("${speed}×".replace(".0×", "×"), color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/** Two or three options on a soft track; the chosen one is a raised white tile. */
@Composable
fun SpeakySegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    radius: Dp = 16.dp,
    fontSize: TextUnit = 14.sp,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(colors.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            val inner = RoundedCornerShape(radius - 4.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .then(if (on) Modifier.shadow(3.dp, inner, ambientColor = Color(0x1417142B), spotColor = Color(0x1417142B)) else Modifier)
                    .clip(inner)
                    .background(if (on) colors.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) colors.textPrimary else colors.textSecondary,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Accent (bars, outlines), soft fill and text-on-soft for a CEFR level, in the redesign's palette. */
data class SpeakyLevelTone(val accent: Color, val soft: Color, val onSoft: Color)

@Composable
fun speakyLevelTone(level: String): SpeakyLevelTone {
    val colors = LocalSpeakMindColors.current
    val dark = colors.surface.luminance() < 0.5f
    return when (level.uppercase()) {
        "A1" -> SpeakyLevelTone(Color(0xFF19B88A), colors.mintSoft, colors.onMintSoft)
        "A2" -> if (dark) SpeakyLevelTone(Color(0xFF6EA8FF), Color(0xFF1C2A47), Color(0xFFB8D2FF))
        else SpeakyLevelTone(Color(0xFF3B82F6), Color(0xFFE0ECFF), Color(0xFF1E4FA8))
        "B1" -> SpeakyLevelTone(colors.coral, colors.coralSoft, colors.onCoralSoft)
        "B2" -> if (dark) SpeakyLevelTone(Color(0xFFFF7EB6), Color(0xFF3A1D2E), Color(0xFFFFB3D4))
        else SpeakyLevelTone(Color(0xFFE0408A), Color(0xFFFCE4EF), Color(0xFF9D174D))
        else -> SpeakyLevelTone(colors.neonCyan, colors.primarySoft, colors.onPrimarySoft)
    }
}

/**
 * A big headword that shrinks until it fits instead of breaking inside a word.
 * One word stays on one line; a phrase may wrap, but only between words.
 */
@Composable
fun SpeakyFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 18.sp,
    textAlign: TextAlign? = null,
) {
    val isPhrase = text.trim().contains(' ')
    var fontSize by remember(text, style.fontSize) { mutableStateOf(style.fontSize) }
    var ready by remember(text, style.fontSize) { mutableStateOf(false) }
    Text(
        text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(
            fontSize = fontSize,
            lineHeight = if (style.lineHeight.isSpecified) fontSize * (style.lineHeight.value / style.fontSize.value) else style.lineHeight,
        ),
        color = color,
        textAlign = textAlign,
        maxLines = if (isPhrase) 3 else 1,
        softWrap = isPhrase,
        onTextLayout = { layout ->
            if (layout.overflowsOrSplitsWord(text) && fontSize.value > minFontSize.value) {
                fontSize = (fontSize.value * 0.9f).coerceAtLeast(minFontSize.value).sp
            } else {
                ready = true
            }
        },
    )
}

private fun TextLayoutResult.overflowsOrSplitsWord(text: String): Boolean {
    if (didOverflowWidth || didOverflowHeight) return true
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()) return true
    }
    return false
}
