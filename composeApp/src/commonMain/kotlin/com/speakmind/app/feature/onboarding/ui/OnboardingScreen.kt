package com.speakmind.app.feature.onboarding.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.ui.theme.levelColorOf
import com.speakmind.app.navigation.OnboardingDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun NavGraphBuilder.onboardingScreen() {
    animatedComposable<OnboardingDestination> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        OnboardingContent(
            uiState = uiState,
            onNameChanged = viewModel::onNameChanged,
            onNameSubmitted = viewModel::onNameSubmitted,
            onLevelSelected = viewModel::onLevelSelected,
            onContinue = viewModel::onContinue,
            onThemeToggle = viewModel::onThemeToggle,
        )
    }
}

private val LEVEL_OPTIONS = listOf(
    "A1" to "Beginner",
    "A2" to "Elementary",
    "B1" to "Intermediate",
    "B2" to "Upper Intermediate",
    "C1" to "Advanced",
)

@Composable
private fun OnboardingContent(
    uiState: OnboardingUiState,
    onNameChanged: (String) -> Unit,
    onNameSubmitted: () -> Unit,
    onLevelSelected: (String) -> Unit,
    onContinue: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val listState = rememberLazyListState()

    val aiMessages = remember {
        listOf(
            "Hey there! \u2728",
            "I'm Sage, your personal AI English tutor.",
            "I'll help you practice English through real conversations \u2014 correcting your mistakes, building your vocabulary, and making learning fun!",
            "But first... what's your name? \uD83D\uDE0A",
        )
    }

    var visibleCount by remember { mutableIntStateOf(0) }
    var showInput by remember { mutableStateOf(false) }
    var showTyping by remember { mutableStateOf(true) }

    // Level step messages
    var levelMsgVisible by remember { mutableIntStateOf(0) }
    var showLevelTyping by remember { mutableStateOf(false) }
    var showLevelPicker by remember { mutableStateOf(false) }

    val levelMessages = remember(uiState.name) {
        listOf(
            "Nice to meet you, ${uiState.name}! \uD83D\uDE04",
            "What's your current English level?",
        )
    }

    LaunchedEffect(Unit) {
        delay(800)
        for (i in aiMessages.indices) {
            showTyping = true
            delay(1200)
            showTyping = false
            visibleCount = i + 1
            delay(400)
        }
        delay(200)
        showInput = true
    }

    // Animate level messages when step changes to LEVEL
    LaunchedEffect(uiState.step) {
        if (uiState.step == OnboardingStep.LEVEL) {
            delay(400)
            for (i in levelMessages.indices) {
                showLevelTyping = true
                delay(1000)
                showLevelTyping = false
                levelMsgVisible = i + 1
                delay(300)
            }
            delay(200)
            showLevelPicker = true
        }
    }

    // Auto-scroll
    LaunchedEffect(visibleCount, showInput, uiState.step, levelMsgVisible, showLevelPicker) {
        delay(100)
        val itemCount = listState.layoutInfo.totalItemsCount
        if (itemCount > 0) {
            listState.animateScrollToItem((itemCount - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        OnboardingParticles()

        Column(modifier = Modifier.fillMaxSize()) {
            // Sage header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.7f))
                    .padding(top = 52.sdp, bottom = 14.sdp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SageAvatar(44)
                    Spacer(modifier = Modifier.width(12.sdp))
                    Column {
                        Text(
                            text = "Sage",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        )
                        Text(
                            text = "Your AI English Tutor",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.neonCyan,
                            )
                        )
                    }
                }
                IconButton(
                    onClick = { onThemeToggle(isDark) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.sdp),
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                        tint = colors.gold,
                        modifier = Modifier.size(22.sdp),
                    )
                }
            }

            // Chat area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 12.sdp),
                verticalArrangement = Arrangement.spacedBy(10.sdp),
            ) {
                // Step 1: Name messages
                items(aiMessages.take(visibleCount)) { msg ->
                    AiBubble(text = msg)
                }

                if (showTyping && uiState.step == OnboardingStep.NAME) {
                    item { TypingBubble() }
                }

                if (showInput && uiState.step == OnboardingStep.NAME) {
                    item {
                        Spacer(modifier = Modifier.height(4.sdp))
                        NameInputCard(
                            name = uiState.name,
                            isValid = uiState.isNameValid,
                            onNameChanged = onNameChanged,
                            onSubmit = onNameSubmitted,
                        )
                    }
                }

                // User name bubble (shown after name is submitted)
                if (uiState.step == OnboardingStep.LEVEL) {
                    item { UserBubble(text = "I'm ${uiState.name}!") }
                }

                // Step 2: Level messages
                if (uiState.step == OnboardingStep.LEVEL) {
                    items(levelMessages.take(levelMsgVisible)) { msg ->
                        AiBubble(text = msg)
                    }

                    if (showLevelTyping) {
                        item { TypingBubble() }
                    }

                    if (showLevelPicker) {
                        item {
                            Spacer(modifier = Modifier.height(4.sdp))
                            LevelSelectionCard(
                                selectedLevel = uiState.selectedLevel,
                                onLevelSelected = onLevelSelected,
                            )
                        }
                    }

                    // User level bubble
                    if (uiState.selectedLevel.isNotEmpty()) {
                        item {
                            val desc = LEVEL_OPTIONS.first { it.first == uiState.selectedLevel }.second
                            UserBubble(text = "${uiState.selectedLevel} — $desc")
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(100.sdp)) }
            }

        }

        // Continue button — step 1: submit name, step 2: finish onboarding
        val showButton = when (uiState.step) {
            OnboardingStep.NAME -> uiState.isNameValid && showInput
            OnboardingStep.LEVEL -> uiState.selectedLevel.isNotEmpty()
        }
        val buttonText = when (uiState.step) {
            OnboardingStep.NAME -> "Continue \u2192"
            OnboardingStep.LEVEL -> "Let's Start Learning! \uD83D\uDE80"
        }
        val buttonAction = when (uiState.step) {
            OnboardingStep.NAME -> onNameSubmitted
            OnboardingStep.LEVEL -> onContinue
        }

        AnimatedVisibility(
            visible = showButton,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, colors.backgroundDark)
                        )
                    )
                    .padding(horizontal = 24.sdp)
                    .padding(top = 20.sdp, bottom = 32.sdp)
            ) {
                Button(
                    onClick = buttonAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.sdp),
                    shape = RoundedCornerShape(16.sdp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.neonCyan,
                        contentColor = colors.backgroundDark,
                    )
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.sdp, 16.sdp, 4.sdp, 16.sdp))
                .background(colors.userBubbleGradient)
                .padding(14.sdp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            )
        }
    }
}

@Composable
private fun LevelSelectionCard(
    selectedLevel: String,
    onLevelSelected: (String) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.sdp),
        verticalArrangement = Arrangement.spacedBy(10.sdp),
    ) {
        LEVEL_OPTIONS.forEach { (level, description) ->
            val isSelected = level == selectedLevel
            val levelColor = levelColorOf(level)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.sdp))
                    .background(
                        if (isSelected) levelColor.copy(alpha = 0.15f)
                        else colors.surface.copy(alpha = 0.3f)
                    )
                    .border(
                        width = if (isSelected) 1.5.sdp else 0.sdp,
                        color = if (isSelected) levelColor.copy(alpha = 0.5f) else Color.Transparent,
                        shape = RoundedCornerShape(14.sdp),
                    )
                    .clickable { onLevelSelected(level) }
                    .padding(horizontal = 16.sdp, vertical = 14.sdp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.sdp))
                        .background(levelColor.copy(alpha = 0.2f))
                        .padding(horizontal = 10.sdp, vertical = 4.sdp)
                ) {
                    Text(
                        text = level,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = levelColor,
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }
                Spacer(modifier = Modifier.width(12.sdp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textPrimary,
                    )
                )
            }
        }
    }
}

@Composable
private fun SageAvatar(size: Int) {
    val colors = LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((size + 10).sdp)
                .clip(CircleShape)
                .background(colors.neonCyan.copy(alpha = glow * 0.3f))
        )
        Box(
            modifier = Modifier
                .size(size.sdp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(colors.neonCyan, colors.neonCyanDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.backgroundDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size / 2.5).ssp,
                )
            )
        }
    }
}

@Composable
private fun AiBubble(text: String) {
    val colors = LocalSpeakMindColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        val bubbleShape = RoundedCornerShape(16.sdp, 16.sdp, 16.sdp, 4.sdp)
        Box(
            modifier = Modifier
                .widthIn(max = 300.sdp)
                .clip(bubbleShape)
                .background(colors.aiBubbleGradient)
                .border(1.sdp, colors.aiBubbleBorder, bubbleShape)
                .padding(14.sdp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.textPrimary,
                    lineHeight = 24.ssp,
                )
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    val colors = LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.sdp, 16.sdp, 16.sdp, 4.sdp))
            .background(colors.aiBubbleGradient)
            .padding(horizontal = 18.sdp, vertical = 14.sdp),
        horizontalArrangement = Arrangement.spacedBy(5.sdp)
    ) {
        repeat(3) { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(8.sdp)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun NameInputCard(
    name: String,
    isValid: Boolean,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.sdp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            placeholder = {
                Text(
                    "Type your name...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted)
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.sdp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.neonCyan.copy(alpha = 0.5f),
                unfocusedBorderColor = colors.surfaceVariant,
                cursorColor = colors.neonCyan,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.surface.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.surface.copy(alpha = 0.3f),
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (isValid) onSubmit() }),
        )
        Spacer(modifier = Modifier.width(10.sdp))
        IconButton(
            onClick = { if (isValid) onSubmit() },
            modifier = Modifier
                .size(48.sdp)
                .clip(CircleShape)
                .background(if (isValid) colors.neonCyan else colors.surfaceVariant)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Continue",
                tint = if (isValid) colors.backgroundDark else colors.textMuted,
                modifier = Modifier.size(22.sdp)
            )
        }
    }
}

@Composable
private fun OnboardingParticles() {
    val colors = LocalSpeakMindColors.current
    val particles = remember {
        List(20) {
            floatArrayOf(
                Random.nextFloat(), Random.nextFloat(),
                Random.nextFloat() * 2f + 0.5f,
                Random.nextFloat() * 0.3f + 0.05f,
                Random.nextFloat() * 0.3f + 0.1f,
            )
        }
    }
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(50000, easing = LinearEasing), RepeatMode.Restart)
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            drawCircle(
                color = colors.neonCyan.copy(alpha = p[3]),
                radius = p[2].dp.toPx(),
                center = Offset(
                    p[0] * size.width + cos(time * p[4] * 0.05f) * 30f,
                    p[1] * size.height + sin(time * p[4] * 0.05f) * 30f,
                )
            )
        }
    }
}
