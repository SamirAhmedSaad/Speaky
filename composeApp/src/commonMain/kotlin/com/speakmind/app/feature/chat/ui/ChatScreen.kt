package com.speakmind.app.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.TtsSpeedButton
import com.speakmind.app.ui.components.rememberInterstitialAdState
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.chatScreen() {
    animatedComposable<ChatDestination> { backStackEntry ->
        val destination = backStackEntry.arguments
        val scenarioId = destination?.getString("scenarioId")
        val viewModel = koinViewModel<ChatViewModel> { parametersOf(scenarioId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        // Re-resolve the AI engine whenever this screen resumes (e.g. returning from AiSetup)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        ChatScreenContent(
            uiState = uiState,
            onInputChanged = viewModel::onInputChanged,
            onSendMessage = { viewModel.onSendMessage() },
            onEndConversation = viewModel::onEndConversation,
            onStartRecording = viewModel::onStartRecording,
            onStopRecording = viewModel::onStopRecording,
            onSpeakMessage = viewModel::speakMessage,
            onOpenSettings = viewModel::onOpenSettings,
        )
    }
}

@Composable
private fun ChatScreenContent(
    uiState: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onEndConversation: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val listState = rememberLazyListState()
    val interstitialAd = rememberInterstitialAdState()

    // Count AI messages and show interstitial every 10
    val aiMessageCount = uiState.messages.count { it.role == MessageRole.ASSISTANT }
    var lastAdShownAtCount by remember { mutableStateOf(0) }

    LaunchedEffect(aiMessageCount) {
        if (aiMessageCount > 0 && aiMessageCount % 10 == 0 && aiMessageCount != lastAdShownAtCount) {
            lastAdShownAtCount = aiMessageCount
            interstitialAd.show()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        // Chat header
        ChatHeader(
            title = uiState.title,
            onEnd = onEndConversation,
            onOpenSettings = onOpenSettings,
        )

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.sdp, vertical = 8.sdp),
            verticalArrangement = Arrangement.spacedBy(8.sdp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                ) {
                    ChatBubble(message = message, onSpeakMessage = onSpeakMessage)
                }
            }

            // Typing indicator
            if (uiState.isGenerating) {
                item {
                    TypingIndicator()
                }
            }

        }

        // Recording indicator
        AnimatedVisibility(
            visible = uiState.isRecording,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            RecordingIndicator()
        }

        // Input area
        ChatInput(
            inputText = uiState.inputText,
            isRecording = uiState.isRecording,
            isGenerating = uiState.isGenerating,
            onInputChanged = onInputChanged,
            onSendMessage = onSendMessage,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
        )
    }
}

@Composable
private fun ChatHeader(
    title: String,
    onEnd: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.95f))
            .padding(horizontal = 4.sdp)
            .padding(top = 48.sdp, bottom = 8.sdp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onEnd) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.textPrimary
            )
        }

        // Sage avatar
        Box(
            modifier = Modifier
                .size(40.sdp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(colors.neonCyan.copy(alpha = 0.6f), colors.neonCyan.copy(alpha = 0.2f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.ssp,
            )
        }

        Spacer(Modifier.width(10.sdp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sage",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = "English Tutor",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.neonCyan),
                maxLines = 1,
            )
        }

        TtsSpeedButton(modifier = Modifier.padding(horizontal = 4.sdp))

        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = colors.textSecondary,
                modifier = Modifier.size(22.sdp),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onSpeakMessage: (String) -> Unit) {
    val colors = LocalSpeakMindColors.current
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.sdp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.sdp,
                            topEnd = 16.sdp,
                            bottomStart = if (isUser) 16.sdp else 4.sdp,
                            bottomEnd = if (isUser) 4.sdp else 16.sdp,
                        )
                    )
                    .background(
                        if (isUser) colors.userBubbleGradient
                        else colors.aiBubbleGradient
                    )
                    .padding(12.sdp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        lineHeight = 22.ssp
                    )
                )
            }

            // Speaker icon for AI messages
            if (!isUser) {
                IconButton(
                    onClick = { onSpeakMessage(message.content) },
                    modifier = Modifier.size(32.sdp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read aloud",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.sdp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator() {
    val colors = LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.magenta.copy(alpha = 0.1f))
            .padding(horizontal = 16.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.sdp)
                .clip(CircleShape)
                .background(colors.magenta.copy(alpha = pulseAlpha))
        )
        Spacer(modifier = Modifier.width(8.sdp))
        Text(
            text = "Listening... speak now",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.magenta,
                fontWeight = FontWeight.Medium,
            )
        )
    }
}

@Composable
private fun TypingIndicator() {
    val colors = LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.sdp, 16.sdp, 16.sdp, 4.sdp))
            .background(colors.aiBubbleGradient)
            .padding(horizontal = 16.sdp, vertical = 12.sdp),
        horizontalArrangement = Arrangement.spacedBy(4.sdp)
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(8.sdp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun ChatInput(
    inputText: String,
    isRecording: Boolean,
    isGenerating: Boolean,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 12.sdp, vertical = 8.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.sdp)
    ) {
        // Mic button — hold to record, release to stop
        Box(
            modifier = Modifier
                .size(44.sdp)
                .clip(CircleShape)
                .background(
                    if (isRecording) colors.magenta.copy(alpha = 0.2f)
                    else Color.Transparent
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onStartRecording()
                            tryAwaitRelease()
                            onStopRecording()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Hold to record",
                tint = if (isRecording) colors.magenta else colors.neonCyan
            )
        }

        // Text input
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChanged,
            placeholder = {
                Text(
                    "Type your message...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textMuted
                    )
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.sdp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.neonCyan.copy(alpha = 0.5f),
                unfocusedBorderColor = colors.surfaceVariant,
                cursorColor = colors.neonCyan,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        // Send button
        IconButton(
            onClick = onSendMessage,
            enabled = inputText.isNotBlank() && !isGenerating,
            modifier = Modifier
                .size(44.sdp)
                .clip(CircleShape)
                .background(
                    if (inputText.isNotBlank()) colors.neonCyan
                    else colors.surfaceVariant
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (inputText.isNotBlank()) colors.backgroundDark
                else colors.textMuted,
                modifier = Modifier.size(20.sdp)
            )
        }
    }
}
