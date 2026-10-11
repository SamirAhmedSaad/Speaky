package com.speakmind.app.feature.community.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Everything the chat screen can ask the view model to do. */
private class RoomActions(vm: RoomChatViewModel) {
    val onInputChanged = vm::onInputChanged
    val onSend = vm::onSendClicked
    val onLoadMore = vm::loadMore
    val onBack = vm::onBack
    val onMic = vm::onMicPressed
    val onCancelRecording = vm::onCancelRecording
    val onSendRecording = vm::onSendRecording
    val onPlayVoice = vm::onPlayVoice
    val onRetry = vm::onRetry
    val onMessageUser = vm::onMessageUser
    val onSignIn = vm::onSignInRequested
    val onToggleMute = vm::onToggleMute
    val onEdit = vm::onEditRequested
    val onCancelEdit = vm::onCancelEdit
    val onDelete = vm::onDeleteRequested
    val onReport = vm::onReportRequested
    val onBlock = vm::onBlockRequested
    val onUnblockOther = vm::onUnblockOther
    val onRetryConnection = vm::onRetryConnection
    val onDailyLimitDismissed = vm::onDailyLimitDismissed
    val onContentWarningDismissed = vm::onContentWarningDismissed
    val onErrorDismissed = vm::onErrorDismissed
    val onInfoDismissed = vm::onInfoDismissed
}

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
        val actions = remember(viewModel) { RoomActions(viewModel) }
        RoomChatContent(uiState = uiState, actions = actions)

        uiState.reportTarget?.let { target ->
            ReportDialog(target = target, onDismiss = viewModel::onReportDismissed, onSubmit = viewModel::onSubmitReport)
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
        uiState.deleteTarget?.let {
            AlertDialog(
                onDismissRequest = viewModel::onDeleteDismissed,
                title = { Text("Delete message?") },
                text = { Text("It will be removed for everyone.") },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = viewModel::onDeleteDismissed) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun RoomChatContent(uiState: RoomChatUiState, actions: RoomActions) {
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
            .collect { index -> if (index <= 1 && !uiState.isInitialLoading) actions.onLoadMore() }
    }

    AutoDismiss(uiState.contentWarning, actions.onContentWarningDismissed)
    AutoDismiss(uiState.error, actions.onErrorDismissed)
    AutoDismiss(uiState.info, actions.onInfoDismissed, 3_500)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
            .imePadding(),
    ) {
        RoomTopBar(uiState = uiState, actions = actions)

        if (uiState.isOffline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.warning.copy(alpha = 0.18f))
                    .clickable { actions.onRetryConnection() }
                    .padding(horizontal = 16.sdp, vertical = 8.sdp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.sdp))
                Text(
                    "Offline — showing saved messages. Tap to retry.",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textPrimary),
                )
            }
        }

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
                                messages.isEmpty() -> Text(
                                    "No messages yet — say hi! 👋",
                                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                                )
                                !uiState.hasMore && !uiState.isOffline -> Text(
                                    "Start of conversation",
                                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                                )
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val fromMe = message.senderId == uiState.currentUserId
                        val nickname = message.senderNickname.ifEmpty { "this user" }
                        MessageBubble(
                            message = message,
                            isFromMe = fromMe,
                            showSender = uiState.kind != RoomKind.DIRECT,
                            isPlaying = uiState.playingMessageId == message.id,
                            isLoadingVoice = uiState.loadingVoiceId == message.id,
                            isSeen = uiState.seenMessageId == message.id,
                            canModerate = !uiState.isGuest,
                            onPlayVoice = { actions.onPlayVoice(message) },
                            onRetry = { actions.onRetry(message.id) },
                            onEdit = { actions.onEdit(message) },
                            onDelete = { actions.onDelete(message) },
                            onReport = { actions.onReport(ReportTarget(message.senderId, nickname, message)) },
                            onBlock = { actions.onBlock(ReportTarget(message.senderId, nickname)) },
                            onSenderClick = { actions.onMessageUser(message.senderId, message.senderNickname) },
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
                                    .clickable { actions.onDailyLimitDismissed() }
                                    .padding(top = 6.sdp, bottom = 4.sdp),
                            )
                        }
                    }
                }
            }
        }

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
                            .clickable { actions.onInputChanged(uiState.inputText + emoji) }
                            .padding(6.sdp),
                    )
                }
            }
        }

        Banner(text = uiState.contentWarning ?: uiState.error, icon = Icons.Default.Warning)
        Banner(text = uiState.info, icon = Icons.Default.CheckCircle, background = colors.neonCyanDark)

        when {
            uiState.isBanned -> NoticeBar("Your account is suspended for breaking community rules.")
            uiState.guestRestricted -> SignInBar(onSignIn = actions.onSignIn)
            uiState.kind == RoomKind.DIRECT && uiState.isDirectBlocked -> BlockedBar(
                blockedByMe = uiState.isOtherBlockedByMe,
                nickname = uiState.otherUser?.nickname ?: "this user",
                onUnblock = actions.onUnblockOther,
            )
            uiState.isRecording -> RecordingBar(
                elapsedMs = uiState.recordingElapsedMs,
                onCancel = actions.onCancelRecording,
                onSend = actions.onSendRecording,
            )
            else -> {
                uiState.editingMessage?.let { editing ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface.copy(alpha = 0.97f))
                            .padding(horizontal = 16.sdp, vertical = 6.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(16.sdp))
                        Column(Modifier.weight(1f).padding(horizontal = 8.sdp)) {
                            Text("Editing message", style = MaterialTheme.typography.labelSmall.copy(color = colors.neonCyan))
                            Text(
                                editing.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
                            )
                        }
                        IconButton(onClick = actions.onCancelEdit) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel edit", tint = colors.textMuted)
                        }
                    }
                }
                InputBar(
                    text = uiState.inputText,
                    placeholder = "Message ${uiState.title}...",
                    showEmojis = showEmojis,
                    allowVoice = uiState.canSendVoice && uiState.editingMessage == null,
                    onToggleEmojis = {
                        if (!showEmojis) keyboardController?.hide()
                        showEmojis = !showEmojis
                    },
                    onFocusText = {
                        showEmojis = false
                        keyboardController?.show()
                    },
                    onInputChanged = actions.onInputChanged,
                    onSend = actions.onSend,
                    onMic = {
                        showEmojis = false
                        actions.onMic()
                    },
                )
            }
        }
    }
}

@Composable
private fun AutoDismiss(value: String?, onDismiss: () -> Unit, delayMs: Long = 4_000) {
    LaunchedEffect(value) {
        if (value != null) {
            kotlinx.coroutines.delay(delayMs)
            onDismiss()
        }
    }
}

@Composable
private fun RoomTopBar(uiState: RoomChatUiState, actions: RoomActions) {
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
        IconButton(onClick = actions.onBack) {
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
                OnlineDot(online = uiState.isOtherOnline, size = 11.sdp, modifier = Modifier.align(Alignment.BottomEnd))
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
                RoomKind.GROUP -> "${uiState.onlineCount} online"
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
        val showMenu = !uiState.isGuest && uiState.kind == RoomKind.DIRECT && uiState.otherUser != null
        if (showMenu) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Chat options", tint = colors.textPrimary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem(
                        if (uiState.muted) "Unmute notifications" else "Mute notifications",
                        if (uiState.muted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    ) {
                        menuOpen = false
                        actions.onToggleMute()
                    }
                    if (uiState.kind == RoomKind.DIRECT) {
                        val other = uiState.otherUser
                        MenuItem("Report ${other?.nickname ?: ""}", Icons.Default.Flag) {
                            menuOpen = false
                            other?.let { actions.onReport(ReportTarget(it.uid, it.nickname)) }
                        }
                        if (uiState.isOtherBlockedByMe) {
                            MenuItem("Unblock", Icons.Default.Block) {
                                menuOpen = false
                                actions.onUnblockOther()
                            }
                        } else {
                            MenuItem("Block", Icons.Default.Block, destructive = true) {
                                menuOpen = false
                                other?.let { actions.onBlock(ReportTarget(it.uid, it.nickname)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) tint else Color.Unspecified) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: RoomMessage,
    isFromMe: Boolean,
    showSender: Boolean,
    isPlaying: Boolean,
    isLoadingVoice: Boolean,
    isSeen: Boolean,
    canModerate: Boolean,
    onPlayVoice: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onSenderClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var actionsOpen by remember { mutableStateOf(false) }
    val hasActions = !message.isPending && (isFromMe || canModerate)
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
                modifier = Modifier.clip(CircleShape).clickable { onSenderClick() },
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
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.sdp))
                        .clickable { onSenderClick() }
                        .padding(start = 4.sdp, bottom = 2.sdp),
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
                            message.isFailed -> Modifier.combinedClickable(onClick = onRetry, onLongClick = onDelete)
                            hasActions -> Modifier.combinedClickable(onClick = {}, onLongClick = { actionsOpen = true })
                            else -> Modifier
                        }
                    )
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            ) {
                Column {
                    when {
                        message.isVoiceExpired -> Text(
                            text = "🎤 Voice message expired",
                            style = MaterialTheme.typography.bodyMedium.copy(color = metaColor, fontStyle = FontStyle.Italic),
                        )
                        message.kind == MessageKind.VOICE -> VoiceRow(
                            durationMs = message.voiceDurationMs,
                            isPlaying = isPlaying,
                            isLoading = isLoadingVoice || message.isPending,
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
                                text = (if (message.isEdited) "edited · " else "") + formatClock(message.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall.copy(color = metaColor, fontSize = 9.ssp),
                            )
                        }
                    }
                }
            }
            if (isSeen) {
                Text(
                    "Seen",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted, fontSize = 9.ssp),
                    modifier = Modifier.padding(top = 2.sdp, end = 4.sdp),
                )
            }
            DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                if (isFromMe) {
                    if (message.kind == MessageKind.TEXT) {
                        MenuItem("Edit", Icons.Default.Edit) {
                            actionsOpen = false
                            onEdit()
                        }
                    }
                    MenuItem("Delete", Icons.Default.Delete, destructive = true) {
                        actionsOpen = false
                        onDelete()
                    }
                } else {
                    if (showSender) {
                        MenuItem("Message privately", Icons.Default.Email) {
                            actionsOpen = false
                            onSenderClick()
                        }
                    }
                    MenuItem("Report message", Icons.Default.Flag) {
                        actionsOpen = false
                        onReport()
                    }
                    MenuItem("Block ${message.senderNickname.ifEmpty { "user" }}", Icons.Default.Block, destructive = true) {
                        actionsOpen = false
                        onBlock()
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(durationMs: Long, isPlaying: Boolean, isLoading: Boolean, tint: Color, onClick: () -> Unit) {
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
                .then(if (!isLoading) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.sdp), color = tint, strokeWidth = 2.sdp)
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play voice message",
                    tint = tint,
                    modifier = Modifier.size(20.sdp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.sdp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
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
private fun Banner(text: String?, icon: ImageVector, background: Color = Color(0xFFB71C1C)) {
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
private fun NoticeBar(text: String) {
    val colors = LocalSpeakMindColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted, textAlign = TextAlign.Center),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(16.sdp),
    )
}

@Composable
private fun SignInBar(onSignIn: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = 16.sdp, vertical = 12.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "You're reading as a visitor.",
            style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
        )
        Spacer(Modifier.height(8.sdp))
        Button(
            onClick = onSignIn,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("G  ", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
            Text("Sign in with Google to chat", fontWeight = FontWeight.SemiBold)
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
                        "“${it.text.take(140)}”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 8.sdp),
                    )
                }
                Text("Why are you reporting this?", style = MaterialTheme.typography.bodyMedium)
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
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
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send voice message",
                tint = colors.backgroundDark,
                modifier = Modifier.size(20.sdp),
            )
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    placeholder: String,
    showEmojis: Boolean,
    allowVoice: Boolean,
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
        val showMic = !hasText && allowVoice
        val enabled = hasText || showMic
        Box(
            modifier = Modifier
                .size(44.sdp)
                .clip(CircleShape)
                .background(
                    if (enabled) Brush.radialGradient(listOf(colors.neonCyan, colors.neonCyanDark))
                    else Brush.radialGradient(listOf(colors.surfaceVariant, colors.surface)),
                    CircleShape,
                )
                .then(if (enabled) Modifier.clickable { if (hasText) onSend() else onMic() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (showMic) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (showMic) "Record voice message" else "Send",
                tint = if (enabled) colors.backgroundDark else colors.textMuted,
                modifier = Modifier.size(20.sdp),
            )
        }
    }
}
