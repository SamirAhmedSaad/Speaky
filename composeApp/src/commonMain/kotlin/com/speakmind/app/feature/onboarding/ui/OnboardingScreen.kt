package com.speakmind.app.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.OnboardingDestination
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.onboardingScreen() {
    animatedComposable<OnboardingDestination> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        OnboardingContent(
            uiState = uiState,
            onNameChanged = viewModel::onNameChanged,
            onLevelSelected = viewModel::onLevelSelected,
            onStart = {
                // Tidies the name ("my name is ahmed" -> "Ahmed") before saving it.
                viewModel.onNameSubmitted()
                viewModel.onContinue()
            },
            onThemeToggle = viewModel::onThemeToggle,
        )
    }
}

private class LevelOption(val code: String, val name: String, val hint: String)

private val LEVEL_OPTIONS = listOf(
    LevelOption("A1", "Beginner", "I know a few words"),
    LevelOption("A2", "Elementary", "Simple everyday sentences"),
    LevelOption("B1", "Intermediate", "I can talk about familiar topics"),
    LevelOption("B2", "Upper intermediate", "I speak fairly fluently"),
    LevelOption("C1", "Advanced", "I want to sound natural"),
)

/** One page: name and level, then "Let's start". */
@Composable
private fun OnboardingContent(
    uiState: OnboardingUiState,
    onNameChanged: (String) -> Unit,
    onLevelSelected: (String) -> Unit,
    onStart: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val focus = LocalFocusManager.current
    val ready = uiState.isNameValid && uiState.selectedLevel.isNotEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 28.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Three steps: welcome (always done), name, level.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(true, uiState.isNameValid, uiState.selectedLevel.isNotEmpty()).forEach { done ->
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(999.dp))
                            .background(if (done) colors.neonCyan else colors.track),
                    )
                }
                SpeakyIconButton(
                    if (isDark) SpeakyIcons.Sun else SpeakyIcons.Moon,
                    if (isDark) "Switch to light mode" else "Switch to dark mode",
                    onClick = { onThemeToggle(isDark) },
                    size = 36.dp,
                    iconSize = 18.dp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nice to meet you!", style = displayStyle(30.sp, lineHeight = 33.sp), color = colors.textPrimary)
                Text("Tell us your name and your English level.", color = colors.textSecondary, fontSize = 15.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Your name")
                NameField(
                    name = uiState.name,
                    error = uiState.nameError,
                    onNameChanged = onNameChanged,
                    onDone = { focus.clearFocus() },
                )
                uiState.nameError?.let {
                    Text(it, color = colors.error, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            FieldLabel("Your level")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LEVEL_OPTIONS.forEach { level ->
                    LevelRow(level, selected = level.code == uiState.selectedLevel, onClick = {
                        focus.clearFocus()
                        onLevelSelected(level.code)
                    })
                }
            }
        }
        SpeakyPrimaryButton("Let's start", onClick = onStart, enabled = ready, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = LocalSpeakMindColors.current.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun NameField(name: String, error: String?, onNameChanged: (String) -> Unit, onDone: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    var focused by remember { mutableStateOf(false) }
    val border = when {
        error != null -> colors.error
        focused || name.isNotBlank() -> colors.neonCyan
        else -> colors.track
    }
    BasicTextField(
        value = name,
        onValueChange = onNameChanged,
        singleLine = true,
        textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(colors.neonCyan),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(2.dp, border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) Text("Type your name", color = colors.textMuted, fontSize = 16.sp)
                inner()
            }
        },
    )
}

@Composable
private fun LevelRow(level: LevelOption, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(shape)
            .background(if (selected) colors.primarySoft.copy(alpha = 0.6f) else colors.surface)
            .border(2.dp, if (selected) colors.neonCyan else colors.surface, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) colors.neonCyan else colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(level.code, color = if (selected) colors.onPrimary else colors.onPrimarySoft, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(level.name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(level.hint, color = colors.textMuted, fontSize = 12.sp)
        }
        if (selected) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(colors.neonCyan), contentAlignment = Alignment.Center) {
                Icon(SpeakyIcons.Check, "Selected", tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
