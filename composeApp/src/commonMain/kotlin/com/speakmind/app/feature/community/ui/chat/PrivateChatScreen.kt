package com.speakmind.app.feature.community.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.data.model.ChannelMessage
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.navigation.ChannelDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import org.koin.compose.viewmodel.koinViewModel

private val emojiPickerItems = listOf(
    "😊", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤩",
    "😄", "😆", "😅", "🤗", "😇", "🤔", "😐", "😑",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟",
    "👍", "👎", "👏", "🙌", "🤝", "✊", "💪", "🙏",
    "👌", "🤞", "✌️", "🤙", "👋", "🫶", "💅", "🫰",
    "🔥", "✨", "🌟", "⭐", "💫", "🎉", "🎊", "🎈",
    "🎯", "🏆", "🌈", "☀️", "🌙", "⚡", "🌊", "🍀",
    "😭", "😢", "😤", "😠", "🤯", "😱", "😨", "😰",
    "🤦", "🤷", "💀", "👀", "👁️", "🫣", "🤫", "😶",
)

fun NavGraphBuilder.channelScreen() {
    animatedComposable<ChannelDestination> {
        val viewModel = koinViewModel<ChannelViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        ChannelContent(
            uiState = uiState,
            onInputChanged = viewModel::onInputChanged,
            onSendClicked = viewModel::onSendClicked,
            onLoadMore = viewModel::loadMore,
            onBack = viewModel::onBack,
            onDailyLimitDismissed = viewModel::onDailyLimitDismissed,
            onContentWarningDismissed = viewModel::onContentWarningDismissed,
        )
    }
}

@Composable
private fun ChannelContent(
    uiState: ChannelUiState,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onDailyLimitDismissed: () -> Unit,
    onContentWarningDismissed: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val listState = rememberLazyListState()
    var showEmojis by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom when a new message is appended
    val lastMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Restore scroll position when loadMore prepends older messages
    var prevMessageCount by remember { mutableIntStateOf(0) }
    var prevLastId by remember { mutableStateOf("") }
    LaunchedEffect(uiState.messages.size) {
        val newSize = uiState.messages.size
        val newLastId = uiState.messages.lastOrNull()?.id ?: ""
        if (prevMessageCount > 0 && newSize > prevMessageCount && newLastId == prevLastId) {
            val added = newSize - prevMessageCount
            listState.scrollToItem(listState.firstVisibleItemIndex + added)
        }
        prevMessageCount = newSize
        prevLastId = newLastId
    }

    // Auto-dismiss content warning after 4 seconds
    LaunchedEffect(uiState.contentWarning) {
        if (uiState.contentWarning != null) {
            kotlinx.coroutines.delay(4_000)
            onContentWarningDismissed()
        }
    }

    // Trigger pagination when user scrolls to the first item.
    // Re-launch when isInitialLoading changes so we capture the fresh value.
    LaunchedEffect(listState, uiState.isInitialLoading) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index == 0 && !uiState.isInitialLoading && !uiState.isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
            .imePadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.surface.copy(alpha = 0.97f),
                            colors.surface.copy(alpha = 0.5f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = 8.sdp, vertical = 10.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Speaky Hub",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "Your global learning community",
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
                )
            }
        }

        // Messages list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.isInitialLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.neonCyan,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.sdp, vertical = 10.sdp),
                    verticalArrangement = Arrangement.spacedBy(6.sdp),
                ) {
                    // Pagination spinner at top
                    if (uiState.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.sdp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.sdp),
                                    color = colors.neonCyan,
                                    strokeWidth = 2.sdp,
                                )
                            }
                        }
                    }

                    items(uiState.messages, key = { it.id }) { message ->
                        ChannelMessageBubble(
                            message = message,
                            isFromMe = message.senderId == uiState.currentUserId,
                        )
                    }

                    if (uiState.dailyLimitReached) {
                        item(key = "daily_limit_banner") {
                            Text(
                                text = "🌙  That's all for today — you've used all your daily messages.\nCome back tomorrow and keep the conversation going! ✨",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFE53935).copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 10.ssp,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.sdp, bottom = 4.sdp),
                            )
                        }
                    }
                }
            }
        }

        // Emoji grid panel
        AnimatedVisibility(
            visible = showEmojis,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.sdp)
                    .background(colors.surface.copy(alpha = 0.98f))
                    .border(
                        width = 1.sdp,
                        color = colors.neonCyan.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(topStart = 16.sdp, topEnd = 16.sdp),
                    ),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.sdp, vertical = 10.sdp),
                    verticalArrangement = Arrangement.spacedBy(2.sdp),
                    horizontalArrangement = Arrangement.spacedBy(2.sdp),
                ) {
                    items(emojiPickerItems) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 22.ssp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.sdp))
                                .clickable { onInputChanged(uiState.inputText + emoji) }
                                .padding(6.sdp),
                        )
                    }
                }
            }
        }

        // Content warning banner
        AnimatedVisibility(
            visible = uiState.contentWarning != null,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFB71C1C).copy(alpha = 0.92f))
                    .padding(horizontal = 16.sdp, vertical = 10.sdp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.sdp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.sdp),
                )
                Text(
                    text = uiState.contentWarning ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, colors.surface.copy(alpha = 0.97f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 12.sdp, vertical = 10.sdp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.sdp),
        ) {
            // Emoji toggle
            Box(
                modifier = Modifier
                    .size(40.sdp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .border(1.sdp, colors.neonCyan.copy(alpha = 0.15f), CircleShape)
                    .clickable {
                        if (!showEmojis) keyboardController?.hide()
                        showEmojis = !showEmojis
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = if (showEmojis) "⌨️" else "😊", fontSize = 18.ssp)
            }

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.sdp))
                    .background(colors.surfaceVariant)
                    .border(1.sdp, colors.neonCyan.copy(alpha = 0.2f), RoundedCornerShape(22.sdp))
                    .clickable {
                        showEmojis = false
                        keyboardController?.show()
                    }
                    .padding(horizontal = 16.sdp, vertical = 10.sdp),
            ) {
                BasicTextField(
                    value = uiState.inputText,
                    onValueChange = onInputChanged,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    keyboardOptions = KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    ),
                    maxLines = 5,
                    decorationBox = { inner ->
                        if (uiState.inputText.isEmpty()) {
                            Text(
                                text = "Message Speaky Hub...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Send button
            val canSend = uiState.inputText.isNotBlank() && !uiState.isSending
            Box(
                modifier = Modifier
                    .size(44.sdp)
                    .clip(CircleShape)
                    .background(
                        if (canSend)
                            Brush.radialGradient(listOf(colors.neonCyan, colors.neonCyanDark))
                        else
                            Brush.radialGradient(listOf(colors.surfaceVariant, colors.surface)),
                        CircleShape,
                    )
                    .then(if (canSend) Modifier.clickable { onSendClicked() } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) colors.backgroundDark else colors.textMuted,
                    modifier = Modifier.size(20.sdp),
                )
            }
        }
    }
}

@Composable
private fun ChannelMessageBubble(
    message: ChannelMessage,
    isFromMe: Boolean,
) {
    val colors = LocalSpeakMindColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isFromMe) {
            UserAvatar(
                nickname = message.senderNickname,
                photoUrl = message.senderPhotoUrl.ifEmpty { null },
                size = 30.sdp,
                borderColor = colors.neonCyan,
            )
            Spacer(modifier = Modifier.width(6.sdp))
        }

        Column(
            horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
        ) {
            if (!isFromMe) {
                Text(
                    text = message.senderNickname,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.neonCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.ssp,
                    ),
                    modifier = Modifier.padding(start = 4.sdp, bottom = 2.sdp),
                )
            }

            val bubbleShape = RoundedCornerShape(
                topStart = 18.sdp,
                topEnd = 18.sdp,
                bottomStart = if (isFromMe) 18.sdp else 4.sdp,
                bottomEnd = if (isFromMe) 4.sdp else 18.sdp,
            )
            Box(
                modifier = Modifier
                    .widthIn(max = 260.sdp)
                    .clip(bubbleShape)
                    .background(if (isFromMe) colors.userBubbleGradient else colors.aiBubbleGradient)
                    .then(
                        if (!isFromMe && colors.aiBubbleBorder != Color.Transparent)
                            Modifier.border(1.sdp, colors.aiBubbleBorder, bubbleShape)
                        else Modifier
                    )
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            ) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isFromMe) colors.backgroundDark else colors.textPrimary,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.sdp))
                    Text(
                        text = formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isFromMe)
                                colors.backgroundDark.copy(alpha = 0.6f)
                            else
                                colors.textMuted,
                            fontSize = 9.ssp,
                        ),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(epochSeconds: Long): String {
    if (epochSeconds == 0L) return ""
    return try {
        val local = Instant.fromEpochSeconds(epochSeconds)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        val h = ((epochSeconds / 3600) % 24).toInt()
        val m = ((epochSeconds / 60) % 60).toInt()
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }
}
