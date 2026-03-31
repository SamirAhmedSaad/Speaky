package com.speakmind.app.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.navigation.ChatDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.SpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.chatScreen() {
    animatedComposable<ChatDestination> { backStackEntry ->
        val destination = backStackEntry.arguments
        val scenarioId = destination?.getString("scenarioId")
        val viewModel = koinViewModel<ChatViewModel> { parametersOf(scenarioId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        ChatScreenContent(
            uiState = uiState,
            onInputChanged = viewModel::onInputChanged,
            onSendMessage = viewModel::onSendMessage,
            onEndConversation = viewModel::onEndConversation,
            onToggleRecording = viewModel::onToggleRecording,
        )
    }
}

@Composable
private fun ChatScreenContent(
    uiState: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onEndConversation: () -> Unit,
    onToggleRecording: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeakMindColors.backgroundGradient)
            .imePadding()
    ) {
        // Chat header
        ChatHeader(
            title = uiState.title,
            onEnd = onEndConversation
        )

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                ) {
                    ChatBubble(message = message)
                }
            }

            // Typing indicator
            if (uiState.isGenerating) {
                item {
                    TypingIndicator()
                }
            }

            // Model status message
            if (uiState.modelStatus.isNotEmpty()) {
                item {
                    Text(
                        text = uiState.modelStatus,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SpeakMindColors.warning
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Input area
        ChatInput(
            inputText = uiState.inputText,
            isRecording = uiState.isRecording,
            isGenerating = uiState.isGenerating,
            onInputChanged = onInputChanged,
            onSendMessage = onSendMessage,
            onToggleRecording = onToggleRecording,
        )
    }
}

@Composable
private fun ChatHeader(title: String, onEnd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpeakMindColors.surface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp)
            .padding(top = 52.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onEnd) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SpeakMindColors.textPrimary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = SpeakMindColors.textPrimary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onEnd) {
            Text(
                text = "End",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = SpeakMindColors.magenta
                )
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                )
                .background(
                    if (isUser) SpeakMindColors.userBubbleGradient
                    else SpeakMindColors.aiBubbleGradient
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .background(SpeakMindColors.aiBubbleGradient)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    .size(8.dp)
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
    onToggleRecording: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpeakMindColors.surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mic button
        IconButton(
            onClick = onToggleRecording,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) SpeakMindColors.magenta.copy(alpha = 0.2f)
                    else Color.Transparent
                )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Toggle microphone",
                tint = if (isRecording) SpeakMindColors.magenta else SpeakMindColors.neonCyan
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
                        color = SpeakMindColors.textMuted
                    )
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SpeakMindColors.neonCyan.copy(alpha = 0.5f),
                unfocusedBorderColor = SpeakMindColors.surfaceVariant,
                cursorColor = SpeakMindColors.neonCyan,
                focusedTextColor = SpeakMindColors.textPrimary,
                unfocusedTextColor = SpeakMindColors.textPrimary,
                focusedContainerColor = SpeakMindColors.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = SpeakMindColors.surfaceVariant.copy(alpha = 0.3f),
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
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (inputText.isNotBlank()) SpeakMindColors.neonCyan
                    else SpeakMindColors.surfaceVariant
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (inputText.isNotBlank()) SpeakMindColors.backgroundDark
                else SpeakMindColors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
