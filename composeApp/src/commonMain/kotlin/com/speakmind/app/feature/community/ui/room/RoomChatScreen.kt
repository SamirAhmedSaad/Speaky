package com.speakmind.app.feature.community.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.community.data.model.MessageKind
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.repository.ReportReason
import com.speakmind.app.feature.community.ui.components.OnlineDot
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.formatClock
import com.speakmind.app.feature.community.ui.components.formatDuration
import com.speakmind.app.feature.community.ui.components.formatLastSeen
import com.speakmind.app.navigation.RoomDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.distinctUntilChanged
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val emojiPickerItems = listOf(
    "😊", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤩",
    "😄", "😆", "😅", "🤗", "😇", "🤔", "😐", "😑",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "👍", "👎", "👏", "🙌", "🤝", "✊", "💪", "🙏",
    "👌", "🤞", "✌️", "🤙", "👋", "🫶", "🔥", "✨",
    "🌟", "⭐", "🎉", "🎊", "🎯", "🏆", "🌈", "☀️",
    "😭", "😢", "😤", "😠", "🤯", "😱", "🤦", "🤷",
)

fun NavGraphBuilder.roomChatScreen() {
    animatedComposable<RoomDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<RoomDestination>()
        val viewModel = koinViewModel<RoomChatViewModel>(key = dest.roomId) {
            parametersOf(dest.roomId, dest.title)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleStartEffect(viewModel) {
            viewModel.onVisibilityChanged(true)
            onStopOrDispose { viewModel.onVisibilityChanged(false) }
        }
        RoomChatContent(
            uiState = uiState,
            onInputChanged = viewModel::onInputChanged,
            onSendClicked = viewModel::onSendClicked,
            onLoadMore = viewModel::loadMore,
            onBack = viewModel::onBack,
            onMicPressed = viewModel::onMicPressed,
            onCancelRecording = viewModel::onCancelRecording,
            onSendRecording = viewModel::onSendRecording,
            onPlayVoice = viewModel::onPlayVoice,
            onRetry = viewModel::onRetry,
            onJoinGroup = viewModel::onJoinGroup,
            onLeaveGroup = viewModel::onLeaveGroup,
            onDailyLimitDismissed = viewModel::onDailyLimitDismissed,
            onContentWarningDismissed = viewModel::onContentWarningDismissed,
            onErrorDismissed = viewModel::onErrorDismissed,
            onInfoDismissed = viewModel::onInfoDismissed,
            onReportRequested = viewModel::onReportRequested,
            onBlockRequested = viewModel::onBlockRequested,
            onUnblockOther = viewModel::onUnblockOther,
        )
        uiState.reportTarget?.let { target ->
            ReportDialog(
                target = target,
                onDismiss = viewModel::onReportDismissed,
                onSubmit = viewModel::onSubmitReport,
            )
        }
        uiState.blockTarget?.let { target ->
            AlertDialog(
                onDismissRequest = viewModel::onBlockDismissed,
                title = { Text("Block ${target.nickname}?") },
                text = { Text("They won't be able to message you privately, and you won't see their messages in groups or Speaky Hub. They won't be told.") },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmBlock) { Text("Block", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = viewModel::onBlockDismissed) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun RoomChatContent(
    uiState: RoomChatUiState,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onMicPressed: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
    onPlayVoice: (RoomMessage) -> Unit,
    onRetry: (String) -> Unit,
    onJoinGroup: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDailyLimitDismissed: () -> Unit,
    onContentWarningDismissed: () -> Unit,
    onErrorDismissed: () -> Unit,
    onInfoDismissed: () -> Unit,
    onReportRequested: (ReportTarget) -> Unit,
    onBlockRequested: (ReportTarget) -> Unit,
    onUnblockOther: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val messages = uiState.visibleMessages
    val listState = rememberLazyListState()
    var showEmojis by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Scroll to the newest message when one is appended at the bottom
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    // Keep position when older pages are prepended
    var prevCount by remember { mutableIntStateOf(0) }
    var prevLastId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.size) {
        val newSize = messages.size
        if (prevCount > 0 && newSize > prevCount && lastMessageId == prevLastId) {
            listState.scrollToItem(listState.firstVisibleItemIndex + (newSize - prevCount))
        }
        prevCount = newSize
        prevLastId = lastMessageId
    }

    // Load older messages when the top is reached
    LaunchedEffect(listState, uiState.isInitialLoading) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> if (index <= 1 && !uiState.isInitialLoading) onLoadMore() }
    }

    LaunchedEffect(uiState.contentWarning) {
        if (uiState.contentWarning != null) {
            kotlinx.coroutines.delay(4_000)
            onContentWarningDismissed()
        }
    }
    LaunchedEffect(uiState.info) {
        if (uiState.info != null) {
            kotlinx.coroutines.delay(3_500)
            onInfoDismissed()
        }
    }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(4_000)
            onErrorDismissed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
            .imePadding(),
    ) {
        RoomTopBar(
            uiState = uiState,
            onBack = onBack,
            onLeaveGroup = onLeaveGroup,
            onReportOther = { uiState.otherUser?.let { onReportRequested(ReportTarget(it.uid, it.nickname)) } },
            onBlockOther = { uiState.otherUser?.let { onBlockRequested(ReportTarget(it.uid, it.nickname)) } },
            onUnblockOther = onUnblockOther,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.isInitialLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = colors.neonCyan)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.sdp, vertical = 10.sdp),
                    verticalArrangement = Arrangement.spacedBy(6.sdp),
                ) {
                    item(key = "top") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.sdp), contentAlignment = Alignment.Center) {
                            when {
                                uiState.isLoadingMore -> CircularProgressIndicator(
                                    modifier = Modifier.size(22.sdp),
                                    color = colors.neonCyan,
                                    strokeWidth = 2.sdp,
                                )
                                !uiState.hasMore || messages.isEmpty() -> Text(
                                    text = if (messages.isEmpty()) "No messages yet — say hi! 👋" else "Start of conversation",
                                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                                )
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isFromMe = message.senderId == uiState.currentUserId,
                            showSender = uiState.kind != RoomKind.DIRECT,
                            isPlaying = uiState.playingMessageId == message.id,
                            onPlayVoice = { onPlayVoice(message) },
                            onRetry = { onRetry(message.id) },
                            onReport = {
                                onReportRequested(ReportTarget(message.senderId, message.senderNickname.ifEmpty { "this user" }, message))
                            },
                            onBlock = {
                                onBlockRequested(ReportTarget(message.senderId, message.senderNickname.ifEmpty { "this user" }))
                            },
                        )
                    }
                    if (uiState.dailyLimitReached) {
                        item(key = "daily_limit_banner") {
                            Text(
                                text = "🌙  That's all for today in Speaky Hub — you've used all your daily messages.\nYou can still chat in groups and private chats! ✨",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colors.error.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 10.ssp,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDailyLimitDismissed() }
                                    .padding(top = 6.sdp, bottom = 4.sdp),
                            )
                        }
                    }
                }
            }
        }

        // Typing indicator
        AnimatedVisibility(visible = uiState.typingText != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = uiState.typingText ?: "",
                style = MaterialTheme.typography.labelSmall.copy(color = colors.neonCyan, fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(horizontal = 18.sdp, vertical = 2.sdp),
            )
        }

        AnimatedVisibility(
            visible = showEmojis && !uiState.isRecording,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.sdp)
                    .background(colors.surface.copy(alpha = 0.98f))
                    .padding(horizontal = 8.sdp, vertical = 10.sdp),
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

        Banner(text = uiState.contentWarning ?: uiState.error, icon = Icons.Default.Warning)
        Banner(text = uiState.info, icon = Icons.Default.CheckCircle, background = colors.neonCyanDark)

        when {
            uiState.kind == RoomKind.DIRECT && uiState.isDirectBlocked -> BlockedBar(
                blockedByMe = uiState.isOtherBlockedByMe,
                nickname = uiState.otherUser?.nickname ?: "this user",
                onUnblock = onUnblockOther,
            )
            !uiState.canPost -> JoinBar(isJoining = uiState.isJoining, onJoin = onJoinGroup)
            uiState.isRecording -> RecordingBar(
                elapsedMs = uiState.recordingElapsedMs,
                onCancel = onCancelRecording,
                onSend = onSendRecording,
            )
            else -> InputBar(
                text = uiState.inputText,
                placeholder = "Message ${uiState.title}...",
                showEmojis = showEmojis,
                onToggleEmojis = {
                    if (!showEmojis) keyboardController?.hide()
                    showEmojis = !showEmojis
                },
                onFocusText = {
                    showEmojis = false
                    keyboardController?.show()
                },
                onInputChanged = onInputChanged,
                onSend = onSendClicked,
                onMic = {
                    showEmojis = false
                    onMicPressed()
                },
            )
        }
    }
}

@Composable
private fun RoomTopBar(
    uiState: RoomChatUiState,
    onBack: () -> Unit,
    onLeaveGroup: () -> Unit,
    onReportOther: () -> Unit,
    onBlockOther: () -> Unit,
    onUnblockOther: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(
                Brush.verticalGradient(
                    listOf(colors.surface.copy(alpha = 0.97f), colors.surface.copy(alpha = 0.5f), Color.Transparent)
                )
            )
            .padding(horizontal = 8.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        if (uiState.kind == RoomKind.DIRECT && uiState.otherUser != null) {
            Box {
                UserAvatar(
                    nickname = uiState.otherUser.nickname,
                    photoUrl = uiState.otherUser.photoUrl,
                    size = 36.sdp,
                    borderColor = colors.neonCyan,
                )
                OnlineDot(
                    online = uiState.isOtherOnline,
                    size = 11.sdp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            Spacer(Modifier.width(10.sdp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiState.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            )
            val subtitle = uiState.typingText ?: when (uiState.kind) {
                RoomKind.CHANNEL -> "Global learning community · ${uiState.onlineCount} online"
                RoomKind.GROUP -> "${uiState.memberCount} members · ${uiState.onlineCount} online"
                RoomKind.DIRECT -> when {
                    uiState.isOtherOnline -> "Online"
                    uiState.otherUser != null -> formatLastSeen(uiState.otherUser.lastSeen)
                    else -> ""
                }
            }
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (uiState.typingText != null || (uiState.kind == RoomKind.DIRECT && uiState.isOtherOnline))
                        colors.neonCyan else colors.textMuted,
                ),
            )
        }
        val showMenu = (uiState.kind == RoomKind.GROUP && uiState.isMember) ||
            (uiState.kind == RoomKind.DIRECT && uiState.otherUser != null)
        if (showMenu) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Chat options", tint = colors.textPrimary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (uiState.kind == RoomKind.GROUP) {
                        DropdownMenuItem(
                            text = { Text("Leave group") },
                            onClick = {
                                menuOpen = false
                                onLeaveGroup()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Report ${uiState.otherUser?.nickname ?: ""}") },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onReportOther()
                            },
                        )
                        if (uiState.isOtherBlockedByMe) {
                            DropdownMenuItem(
                                text = { Text("Unblock") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onUnblockOther()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Block", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    onBlockOther()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: RoomMessage,
    isFromMe: Boolean,
    showSender: Boolean,
    isPlaying: Boolean,
    onPlayVoice: () -> Unit,
    onRetry: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var actionsOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isFromMe && showSender) {
            UserAvatar(
                nickname = message.senderNickname.ifEmpty { "?" },
                photoUrl = message.senderPhotoUrl,
                size = 30.sdp,
                borderColor = colors.neonCyan,
            )
            Spacer(modifier = Modifier.width(6.sdp))
        }
        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start) {
            if (!isFromMe && showSender && message.senderNickname.isNotEmpty()) {
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
            val shape = RoundedCornerShape(
                topStart = 18.sdp,
                topEnd = 18.sdp,
                bottomStart = if (isFromMe) 18.sdp else 4.sdp,
                bottomEnd = if (isFromMe) 4.sdp else 18.sdp,
            )
            val contentColor = if (isFromMe) colors.backgroundDark else colors.textPrimary
            val metaColor = if (isFromMe) colors.backgroundDark.copy(alpha = 0.6f) else colors.textMuted
            Box(
                modifier = Modifier
                    .widthIn(max = 260.sdp)
                    .clip(shape)
                    .background(if (isFromMe) colors.userBubbleGradient else colors.aiBubbleGradient)
                    .then(
                        if (!isFromMe && colors.aiBubbleBorder != Color.Transparent)
                            Modifier.border(1.sdp, colors.aiBubbleBorder, shape) else Modifier
                    )
                    .then(
                        when {
                            message.isFailed -> Modifier.clickable { onRetry() }
                            !isFromMe -> Modifier.combinedClickable(onClick = {}, onLongClick = { actionsOpen = true })
                            else -> Modifier
                        }
                    )
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            ) {
                Column {
                    when {
                        message.kind == MessageKind.VOICE && message.isVoiceExpired -> Text(
                            text = "🎤 Voice message expired",
                            style = MaterialTheme.typography.bodyMedium.copy(color = metaColor, fontStyle = FontStyle.Italic),
                        )
                        message.kind == MessageKind.VOICE -> VoiceRow(
                            durationMs = message.voiceDurationMs,
                            isPlaying = isPlaying,
                            isReady = message.voiceUrl != null,
                            tint = contentColor,
                            onClick = onPlayVoice,
                        )
                        else -> Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.sdp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.sdp),
                    ) {
                        when {
                            message.isFailed -> {
                                Icon(Icons.Default.ErrorOutline, null, tint = colors.error, modifier = Modifier.size(11.sdp))
                                Text(
                                    "Not sent · tap to retry",
                                    style = MaterialTheme.typography.labelSmall.copy(color = colors.error, fontSize = 9.ssp),
                                )
                            }
                            message.isPending -> Icon(
                                Icons.Default.Schedule, contentDescription = "Sending",
                                tint = metaColor, modifier = Modifier.size(11.sdp),
                            )
                            else -> Text(
                                text = formatClock(message.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall.copy(color = metaColor, fontSize = 9.ssp),
                            )
                        }
                    }
                }
            }
            DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Report message") },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                    onClick = {
                        actionsOpen = false
                        onReport()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Block ${message.senderNickname.ifEmpty { "user" }}", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        actionsOpen = false
                        onBlock()
                    },
                )
            }
        }
    }
}

@Composable
private fun VoiceRow(durationMs: Long, isPlaying: Boolean, isReady: Boolean, tint: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.sdp),
        modifier = Modifier.widthIn(min = 150.sdp),
    ) {
        Box(
            modifier = Modifier
                .size(32.sdp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f))
                .then(if (isReady) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (isReady) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play voice message",
                    tint = tint,
                    modifier = Modifier.size(20.sdp),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.sdp), color = tint, strokeWidth = 2.sdp)
            }
        }
        // Simple static waveform
        Row(horizontalArrangement = Arrangement.spacedBy(2.sdp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            listOf(6, 12, 9, 16, 11, 7, 14, 10, 5, 12, 8, 13).forEach { h ->
                Box(
                    Modifier
                        .width(3.sdp)
                        .height(h.sdp)
                        .clip(RoundedCornerShape(2.sdp))
                        .background(tint.copy(alpha = if (isPlaying) 0.9f else 0.45f))
                )
            }
        }
        Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall.copy(color = tint))
    }
}

@Composable
private fun Banner(
    text: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color = Color(0xFFB71C1C),
) {
    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background.copy(alpha = 0.92f))
                .padding(horizontal = 16.sdp, vertical = 10.sdp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.sdp))
            Text(
                text = text ?: "",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BlockedBar(blockedByMe: Boolean, nickname: String, onUnblock: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(14.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (blockedByMe) "You blocked $nickname." else "You can't reply to this conversation.",
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
        )
        if (blockedByMe) {
            TextButton(onClick = onUnblock) { Text("Unblock", color = colors.neonCyan, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ReportDialog(
    target: ReportTarget,
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String, Boolean) -> Unit,
) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var details by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.message != null) "Report message" else "Report ${target.nickname}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                target.message?.takeIf { it.kind == MessageKind.TEXT }?.let {
                    Text(
                        "\u201C${it.text.take(140)}\u201D",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 8.sdp),
                    )
                }
                Text("Why are you reporting this?", style = MaterialTheme.typography.bodyMedium)
                ReportReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { reason = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 500) details = it },
                    label = { Text("More details (optional)") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { alsoBlock = !alsoBlock }.padding(top = 6.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = alsoBlock, onCheckedChange = { alsoBlock = it })
                    Text("Also block ${target.nickname}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { reason?.let { onSubmit(it, details, alsoBlock) } }, enabled = reason != null) {
                Text("Send report", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun JoinBar(isJoining: Boolean, onJoin: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(12.sdp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onJoin,
            enabled = !isJoining,
            colors = ButtonDefaults.buttonColors(containerColor = colors.neonCyan, contentColor = colors.backgroundDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isJoining) "Joining…" else "Join group to chat", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecordingBar(elapsedMs: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = 12.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.sdp),
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel recording", tint = colors.error)
        }
        Box(Modifier.size(10.sdp).background(colors.error, CircleShape))
        Text(
            text = "Recording  ${formatDuration(elapsedMs)}",
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(44.sdp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(colors.neonCyan, colors.neonCyanDark)))
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice message", tint = colors.backgroundDark, modifier = Modifier.size(20.sdp))
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    placeholder: String,
    showEmojis: Boolean,
    onToggleEmojis: () -> Unit,
    onFocusText: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.surface.copy(alpha = 0.97f))))
            .navigationBarsPadding()
            .padding(horizontal = 12.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        Box(
            modifier = Modifier
                .size(40.sdp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.15f), CircleShape)
                .clickable { onToggleEmojis() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (showEmojis) "⌨️" else "😊", fontSize = 18.ssp)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.sdp))
                .background(colors.surfaceVariant)
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.2f), RoundedCornerShape(22.sdp))
                .clickable { onFocusText() }
                .padding(horizontal = 16.sdp, vertical = 10.sdp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onInputChanged,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                ),
                maxLines = 5,
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val hasText = text.isNotBlank()
        Box(
            modifier = Modifier
                .size(44.sdp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(colors.neonCyan, colors.neonCyanDark)), CircleShape)
                .clickable { if (hasText) onSend() else onMic() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                contentDescription = if (hasText) "Send" else "Record voice message",
                tint = colors.backgroundDark,
                modifier = Modifier.size(20.sdp),
            )
        }
    }
}
