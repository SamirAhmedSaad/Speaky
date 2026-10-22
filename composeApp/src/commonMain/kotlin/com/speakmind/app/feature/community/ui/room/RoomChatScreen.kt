package com.speakmind.app.feature.community.ui.room

import com.speakmind.app.ui.components.rememberInterstitialAdState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.community.data.model.MessageKind
import com.speakmind.app.feature.community.data.model.REACTION_EMOJIS
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.repository.ReportReason
import com.speakmind.app.feature.community.ui.components.OnlineDot
import com.speakmind.app.feature.community.ui.components.ResizeWindowForKeyboard
import com.speakmind.app.feature.community.ui.components.ProfileSheet
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.dayKey
import com.speakmind.app.feature.community.ui.components.formatClock
import com.speakmind.app.feature.community.ui.components.formatDayLabel
import com.speakmind.app.feature.community.ui.components.formatDuration
import com.speakmind.app.feature.community.ui.components.formatLastSeen
import com.speakmind.app.config.AppConfig
import com.speakmind.app.feature.community.ui.components.GoogleSignInPill
import com.speakmind.app.feature.community.ui.auth.GoogleIdResult
import com.speakmind.app.feature.community.ui.auth.rememberGoogleSignIn
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

/** Messages from the same person within this gap are drawn as one group. */
private const val GROUP_GAP_MS = 5 * 60 * 1000L

/** Everything the chat screen can ask the view model to do. */
private class RoomActions(vm: RoomChatViewModel, googlePicker: suspend () -> GoogleIdResult) {
    val onInputChanged = vm::onInputChanged
    val onSend = vm::onSendClicked
    val onLoadMore = vm::loadMore
    val onBack = vm::onBack
    val onMic = vm::onMicPressed
    val onCancelRecording = vm::onCancelRecording
    val onSendRecording = vm::onSendRecording
    val onPlayVoice = vm::onPlayVoice
    val onSeekVoice = vm::onSeekVoice
    val onCycleVoiceSpeed = vm::onCycleVoiceSpeed
    val onRetry = vm::onRetry
    val onMessageUser = vm::onMessageUser
    val onSignIn: () -> Unit = { vm.onGoogleSignIn(googlePicker) }
    val onToggleMute = vm::onToggleMute
    val onEdit = vm::onEditRequested
    val onCancelEdit = vm::onCancelEdit
    val onReply = vm::onReplyRequested
    val onCancelReply = vm::onCancelReply
    val onReaction = vm::onReactionClicked
    val onUserClicked = vm::onUserClicked
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

/** Player state for the one voice message that is loaded; other rows get null and don't recompose. */
@Immutable
private data class VoicePlaybackUi(
    val isPaused: Boolean,
    val progress: Float,
    val positionMs: Long,
    val speed: Float,
)

/** A row in the message list. */
private sealed interface ChatItem {
    val key: String
    data class DayHeader(val label: String, override val key: String) : ChatItem
    data class Message(
        val message: RoomMessage,
        val firstInGroup: Boolean,
        val lastInGroup: Boolean,
    ) : ChatItem {
        override val key: String get() = message.id
    }
}

private fun buildItems(messages: List<RoomMessage>): List<ChatItem> {
    val items = ArrayList<ChatItem>(messages.size + 8)
    var lastDay = Long.MIN_VALUE
    messages.forEachIndexed { i, m ->
        val day = dayKey(m.createdAtMillis)
        if (day != lastDay) {
            items += ChatItem.DayHeader(formatDayLabel(m.createdAtMillis), "day-$day")
            lastDay = day
        }
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        val samePrev = prev != null && prev.senderId == m.senderId &&
            m.createdAtMillis - prev.createdAtMillis < GROUP_GAP_MS && dayKey(prev.createdAtMillis) == day
        val sameNext = next != null && next.senderId == m.senderId &&
            next.createdAtMillis - m.createdAtMillis < GROUP_GAP_MS && dayKey(next.createdAtMillis) == day
        items += ChatItem.Message(m, firstInGroup = !samePrev, lastInGroup = !sameNext)
    }
    return items
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
        val googlePicker = rememberGoogleSignIn(AppConfig.googleWebClientId)
        val actions = remember(viewModel, googlePicker) { RoomActions(viewModel, googlePicker) }
        ResizeWindowForKeyboard()
        RoomChatContent(uiState = uiState, actions = actions)

        val gate = rememberInterstitialAdState()
        uiState.profileUser?.let { user ->
            val isMe = user.uid == uiState.currentUserId
            ProfileSheet(
                user = user,
                online = user.uid in uiState.onlineUserIds,
                isBlocked = user.uid in uiState.blockedUserIds,
                onDismiss = viewModel::onProfileDismissed,
                onMessage = if (!isMe && !(uiState.kind == RoomKind.DIRECT && uiState.otherUser?.uid == user.uid)) {
                    {
                        viewModel.onProfileDismissed()
                        gate.unlock("dm:${user.uid}", "Message ${user.nickname}", everyTime = true) { viewModel.onMessageUser(user.uid, user.nickname) }
                    }
                } else null,
                onReport = if (!isMe && !uiState.isGuest) {
                    { viewModel.onProfileDismissed(); viewModel.onReportRequested(ReportTarget(user.uid, user.nickname)) }
                } else null,
                onBlockToggle = if (!isMe && !uiState.isGuest) {
                    {
                        viewModel.onProfileDismissed()
                        if (user.uid in uiState.blockedUserIds) viewModel.onUnblockUser(user.uid, user.nickname)
                        else viewModel.onBlockRequested(ReportTarget(user.uid, user.nickname))
                    }
                } else null,
            )
        }
        uiState.reportTarget?.let { target ->
            ReportDialog(target = target, onDismiss = viewModel::onReportDismissed, onSubmit = viewModel::onSubmitReport)
        }
        uiState.blockTarget?.let { target ->
            AlertDialog(
                onDismissRequest = viewModel::onBlockDismissed,
                title = { Text("Block ${target.nickname}?") },
                text = { Text("They won't be able to message you privately, and you won't see their messages in Speaky Hub. They won't be told.") },
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
    val items = remember(messages) { buildItems(messages) }
    val listState = rememberLazyListState()
    var showEmojis by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // reverseLayout: index 0 is the newest message at the bottom, so a chat opens on its latest
    // messages instantly (no scroll through the history) and older pages append without jumps.
    val reversedItems = remember(items) { items.asReversed() }
    val scope = rememberCoroutineScope()
    val lastMessage = messages.lastOrNull()
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    var unseenBelow by remember { mutableIntStateOf(0) }
    LaunchedEffect(lastMessage?.id) {
        val newest = lastMessage ?: return@LaunchedEffect
        if (atBottom || newest.senderId == uiState.currentUserId) listState.animateScrollToItem(0) else unseenBelow++
    }
    LaunchedEffect(atBottom) { if (atBottom) unseenBelow = 0 }

    // Load older messages as the top (the end of the reversed list) comes into view
    LaunchedEffect(listState, uiState.isInitialLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (!uiState.isInitialLoading && last >= listState.layoutInfo.totalItemsCount - 4) actions.onLoadMore()
            }
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
        ChatHeader(uiState = uiState, actions = actions)

        if (uiState.isOffline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.warning.copy(alpha = 0.15f))
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
                ChatSkeleton()
            } else if (messages.isEmpty()) {
                EmptyChat(uiState)
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.sdp, vertical = 8.sdp),
                ) {
                    if (uiState.dailyLimitReached) {
                        item(key = "daily_limit_banner") {
                            Text(
                                text = "🌙  That's all for today in Speaky Hub. You can still send private messages! ✨",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colors.error.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { actions.onDailyLimitDismissed() }
                                    .padding(vertical = 8.sdp),
                            )
                        }
                    }
                    items(reversedItems, key = { it.key }) { item ->
                        // Smoothly slides rows into place when messages arrive, get deleted or a page loads.
                        Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow))) {
                        when (item) {
                            is ChatItem.DayHeader -> DaySeparator(item.label)
                            is ChatItem.Message -> {
                                val message = item.message
                                val fromMe = message.senderId == uiState.currentUserId
                                val nickname = message.senderNickname.ifEmpty { "this user" }
                                MessageRow(
                                    message = message,
                                    isFromMe = fromMe,
                                    showSender = uiState.kind != RoomKind.DIRECT,
                                    firstInGroup = item.firstInGroup,
                                    lastInGroup = item.lastInGroup,
                                    playback = if (uiState.playingMessageId == message.id) {
                                        VoicePlaybackUi(
                                            isPaused = uiState.isVoicePaused,
                                            progress = uiState.playbackProgress,
                                            positionMs = uiState.playbackPositionMs,
                                            speed = uiState.playbackSpeed,
                                        )
                                    } else {
                                        null
                                    },
                                    isLoadingVoice = uiState.loadingVoiceId == message.id,
                                    tick = when {
                                        !fromMe || message.isPending || message.isFailed -> Tick.NONE
                                        uiState.kind == RoomKind.DIRECT && message.createdAtMillis <= uiState.otherLastReadAt -> Tick.SEEN
                                        else -> Tick.SENT
                                    },
                                    currentUserId = uiState.currentUserId,
                                    canInteract = uiState.canPost,
                                    isGuest = uiState.isGuest,
                                    actions = actions,
                                    onReport = { actions.onReport(ReportTarget(message.senderId, nickname, message)) },
                                    onBlock = { actions.onBlock(ReportTarget(message.senderId, nickname)) },
                                )
                            }
                        }
                    }
                    }
                    item(key = "top") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.sdp), contentAlignment = Alignment.Center) {
                            if (uiState.isLoadingMore) {
                                CircularProgressIndicator(Modifier.size(22.sdp), color = colors.neonCyan, strokeWidth = 2.sdp)
                            } else if (!uiState.hasMore && !uiState.isOffline) {
                                Text(
                                    if (uiState.kind == RoomKind.DIRECT) "This is the start of your conversation" else "Welcome to Speaky Hub 🌍",
                                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                                )
                            }
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom && messages.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.sdp, bottom = 12.sdp),
            ) {
                JumpToNewestButton(unseen = unseenBelow, onClick = { scope.launch { listState.animateScrollToItem(0) } })
            }
        }

        AnimatedVisibility(visible = uiState.typingText != null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.sdp, vertical = 4.sdp),
            ) {
                TypingDots()
                Spacer(Modifier.width(6.sdp))
                Text(
                    text = uiState.typingText ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.neonCyan, fontStyle = FontStyle.Italic),
                )
            }
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
            uiState.guestRestricted -> SignInBar(onSignIn = actions.onSignIn, loading = uiState.isSigningIn)
            uiState.kind == RoomKind.DIRECT && uiState.isDirectBlocked -> BlockedBar(
                blockedByMe = uiState.isOtherBlockedByMe,
                nickname = uiState.otherUser?.nickname ?: "this user",
                onUnblock = actions.onUnblockOther,
            )
            uiState.isRecording -> RecordingBar(
                elapsedMs = uiState.recordingElapsedMs,
                levels = uiState.recordingLevels,
                onCancel = actions.onCancelRecording,
                onSend = actions.onSendRecording,
            )
            else -> {
                uiState.replyingTo?.let { replying ->
                    ComposerContextBar(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        title = "Replying to ${replying.senderNickname.ifEmpty { "yourself" }.takeIf { replying.senderId != uiState.currentUserId } ?: "yourself"}",
                        text = if (replying.kind == MessageKind.VOICE) "🎤 Voice message" else replying.text,
                        onClose = actions.onCancelReply,
                    )
                }
                uiState.editingMessage?.let { editing ->
                    ComposerContextBar(
                        icon = Icons.Default.Edit,
                        title = "Editing message",
                        text = editing.text,
                        onClose = actions.onCancelEdit,
                    )
                }
                InputBar(
                    focusKey = uiState.replyingTo?.id ?: uiState.editingMessage?.id,
                    text = uiState.inputText,
                    placeholder = if (uiState.kind == RoomKind.DIRECT) "Message ${uiState.title}" else "Message Speaky Hub",
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

// ---------------- Header ----------------

@Composable
private fun ChatHeader(uiState: RoomChatUiState, actions: RoomActions) {
    val colors = LocalSpeakMindColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val other = uiState.otherUser
    Column(Modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.85f))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.sdp, vertical = 8.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.sdp))
                    .then(if (other != null) Modifier.clickable { actions.onUserClicked(other.uid) } else Modifier)
                    .padding(vertical = 2.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.kind == RoomKind.DIRECT && other != null) {
                    Box {
                        UserAvatar(nickname = other.nickname, photoUrl = other.photoUrl, size = 40.sdp, borderColor = colors.neonCyan)
                        OnlineDot(online = uiState.isOtherOnline, size = 12.sdp, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                } else {
                    Box(
                        Modifier
                            .size(40.sdp)
                            .clip(RoundedCornerShape(12.sdp))
                            .background(Brush.linearGradient(listOf(colors.neonCyan, colors.magenta))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.sdp))
                    }
                }
                Spacer(Modifier.width(10.sdp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = uiState.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                    )
                    val subtitle = uiState.typingText ?: when (uiState.kind) {
                        RoomKind.DIRECT -> when {
                            uiState.isOtherOnline -> "Online"
                            other != null && other.lastSeen > 0 -> formatLastSeen(other.lastSeen)
                            else -> "Private chat"
                        }
                        else -> if (uiState.onlineCount > 0) "${uiState.onlineCount} online · general chat"
                                else "General chat for all learners"
                    }
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (uiState.typingText != null || (uiState.kind == RoomKind.DIRECT && uiState.isOtherOnline))
                                colors.success else colors.textMuted,
                        ),
                    )
                }
            }
            if (!uiState.isGuest && uiState.kind == RoomKind.DIRECT && other != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Chat options", tint = colors.textPrimary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem("View profile", Icons.Default.Person) {
                            menuOpen = false
                            actions.onUserClicked(other.uid)
                        }
                        MenuItem(
                            if (uiState.muted) "Unmute notifications" else "Mute notifications",
                            if (uiState.muted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        ) {
                            menuOpen = false
                            actions.onToggleMute()
                        }
                        MenuItem("Report ${other.nickname}", Icons.Default.Flag) {
                            menuOpen = false
                            actions.onReport(ReportTarget(other.uid, other.nickname))
                        }
                        if (uiState.isOtherBlockedByMe) {
                            MenuItem("Unblock", Icons.Default.Block) {
                                menuOpen = false
                                actions.onUnblockOther()
                            }
                        } else {
                            MenuItem("Block", Icons.Default.Block, destructive = true) {
                                menuOpen = false
                                actions.onBlock(ReportTarget(other.uid, other.nickname))
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.neonCyan.copy(alpha = 0.12f))
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

// ---------------- Messages ----------------

private enum class Tick { NONE, SENT, SEEN }

@Composable
private fun DaySeparator(label: String) {
    val colors = LocalSpeakMindColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 10.sdp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .clip(RoundedCornerShape(10.sdp))
                .background(colors.surfaceVariant.copy(alpha = 0.8f))
                .padding(horizontal = 12.sdp, vertical = 4.sdp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: RoomMessage,
    isFromMe: Boolean,
    showSender: Boolean,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    playback: VoicePlaybackUi?,
    isLoadingVoice: Boolean,
    tick: Tick,
    currentUserId: String,
    canInteract: Boolean,
    isGuest: Boolean,
    actions: RoomActions,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    val avatarSize: Dp = 30.sdp
    val showAvatarColumn = showSender && !isFromMe

    // Swipe right to reply (like WhatsApp): the bubble follows the finger, an arrow fades in,
    // and releasing past the threshold quotes the message in the composer.
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { 64.sdp.toPx() }
    var armed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (firstInGroup) 8.sdp else 2.sdp),
    ) {
    Icon(
        Icons.AutoMirrored.Filled.Reply,
        contentDescription = null,
        tint = colors.neonCyan,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 6.sdp)
            .size(22.sdp)
            .graphicsLayer {
                val p = (swipe.value / thresholdPx).coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
            },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(swipe.value.roundToInt(), 0) }
            .then(
                if (canInteract && !message.isPending && !message.isFailed) {
                    Modifier.pointerInput(message.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipe.value >= thresholdPx) actions.onReply(message)
                                armed = false
                                scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.6f)) }
                            },
                            onDragCancel = {
                                armed = false
                                scope.launch { swipe.animateTo(0f) }
                            },
                        ) { change, dragAmount ->
                            val next = (swipe.value + dragAmount).coerceIn(0f, thresholdPx * 1.4f)
                            if (next > 0f) change.consume()
                            scope.launch { swipe.snapTo(next) }
                            if (!armed && next >= thresholdPx) {
                                armed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (armed && next < thresholdPx) {
                                armed = false
                            }
                        }
                    }
                } else Modifier
            ),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (showAvatarColumn) {
            if (lastInGroup) {
                Box(Modifier.clip(CircleShape).clickable { actions.onUserClicked(message.senderId) }) {
                    UserAvatar(
                        nickname = message.senderNickname.ifEmpty { "?" },
                        photoUrl = message.senderPhotoUrl,
                        size = avatarSize,
                        borderColor = colors.neonCyan,
                    )
                }
            } else {
                Spacer(Modifier.width(avatarSize))
            }
            Spacer(Modifier.width(6.sdp))
        }
        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start) {
            if (showAvatarColumn && firstInGroup && message.senderNickname.isNotEmpty()) {
                Text(
                    text = message.senderNickname,
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.neonCyan, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.sdp))
                        .clickable { actions.onUserClicked(message.senderId) }
                        .padding(start = 6.sdp, bottom = 2.sdp),
                )
            }
            val big = 18.sdp
            val small = 5.sdp
            val shape = if (isFromMe) {
                RoundedCornerShape(topStart = big, topEnd = if (firstInGroup) big else small, bottomStart = big, bottomEnd = if (lastInGroup) small else small)
            } else {
                RoundedCornerShape(topStart = if (firstInGroup) big else small, topEnd = big, bottomStart = if (lastInGroup) small else small, bottomEnd = big)
            }
            val contentColor = if (isFromMe) colors.backgroundDark else colors.textPrimary
            val metaColor = if (isFromMe) colors.backgroundDark.copy(alpha = 0.6f) else colors.textMuted
            Box {
                Column(
                    modifier = Modifier
                        .widthIn(max = 270.sdp)
                        .clip(shape)
                        .background(if (isFromMe) colors.userBubbleGradient else colors.aiBubbleGradient)
                        .then(
                            if (!isFromMe && colors.aiBubbleBorder != Color.Transparent)
                                Modifier.border(1.sdp, colors.aiBubbleBorder, shape) else Modifier
                        )
                        .combinedClickable(
                            onClick = { if (message.isFailed) actions.onRetry(message.id) },
                            onLongClick = { if (!message.isPending) menuOpen = true },
                        )
                        .padding(horizontal = 12.sdp, vertical = 8.sdp),
                ) {
                    message.replyTo?.let { reply ->
                        Column(
                            Modifier
                                .padding(bottom = 6.sdp)
                                .clip(RoundedCornerShape(8.sdp))
                                .background(contentColor.copy(alpha = 0.08f))
                                .border(width = 0.sdp, color = Color.Transparent, shape = RoundedCornerShape(8.sdp))
                                .padding(start = 0.sdp),
                        ) {
                            Row {
                                Box(Modifier.width(3.sdp).height(36.sdp).background(if (isFromMe) contentColor.copy(alpha = 0.5f) else colors.neonCyan))
                                Column(Modifier.padding(horizontal = 8.sdp, vertical = 4.sdp)) {
                                    Text(
                                        reply.senderNickname.ifEmpty { "Reply" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isFromMe) contentColor else colors.neonCyan,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                    Text(
                                        when {
                                            reply.isVoice -> "🎤 Voice message"
                                            reply.text.isEmpty() -> "Original message"
                                            else -> reply.text
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall.copy(color = metaColor),
                                    )
                                }
                            }
                        }
                    }
                    when {
                        message.isVoiceExpired -> Text(
                            "🎤 Voice message expired",
                            style = MaterialTheme.typography.bodyMedium.copy(color = metaColor, fontStyle = FontStyle.Italic),
                        )
                        message.kind == MessageKind.VOICE -> VoiceMessage(
                            message = message,
                            playback = playback,
                            isLoading = isLoadingVoice || message.isPending,
                            tint = contentColor,
                            onPlayPause = { actions.onPlayVoice(message) },
                            onSeek = { actions.onSeekVoice(message, it) },
                            onSpeed = actions.onCycleVoiceSpeed,
                        )
                        else -> Text(message.text, style = MaterialTheme.typography.bodyMedium.copy(color = contentColor))
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 2.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.sdp),
                    ) {
                        when {
                            message.isFailed -> {
                                Icon(Icons.Default.ErrorOutline, null, tint = colors.error, modifier = Modifier.size(12.sdp))
                                Text("Not sent · tap to retry", style = MaterialTheme.typography.labelSmall.copy(color = colors.error, fontSize = 9.ssp))
                            }
                            message.isPending -> Icon(Icons.Default.Schedule, "Sending", tint = metaColor, modifier = Modifier.size(12.sdp))
                            else -> {
                                Text(
                                    (if (message.isEdited) "edited  " else "") + formatClock(message.createdAtMillis),
                                    style = MaterialTheme.typography.labelSmall.copy(color = metaColor, fontSize = 9.ssp),
                                )
                                when (tick) {
                                    Tick.SENT -> Icon(Icons.Default.Done, "Sent", tint = metaColor, modifier = Modifier.size(13.sdp))
                                    Tick.SEEN -> Icon(Icons.Default.DoneAll, "Seen", tint = Color(0xFF1E88E5), modifier = Modifier.size(14.sdp))
                                    Tick.NONE -> Unit
                                }
                            }
                        }
                    }
                }

                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canInteract) {
                        Row(Modifier.padding(horizontal = 8.sdp, vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(2.sdp)) {
                            REACTION_EMOJIS.forEach { emoji ->
                                val mine = currentUserId in message.reactions[emoji].orEmpty()
                                Text(
                                    emoji,
                                    fontSize = 20.ssp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (mine) colors.neonCyan.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            menuOpen = false
                                            actions.onReaction(message, emoji)
                                        }
                                        .padding(6.sdp),
                                )
                            }
                        }
                        HorizontalDivider()
                        MenuItem("Reply", Icons.AutoMirrored.Filled.Reply) {
                            menuOpen = false
                            actions.onReply(message)
                        }
                    }
                    if (message.kind == MessageKind.TEXT) {
                        MenuItem("Copy", Icons.Default.ContentCopy) {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(message.text))
                        }
                    }
                    if (isFromMe) {
                        if (message.kind == MessageKind.TEXT) {
                            MenuItem("Edit", Icons.Default.Edit) {
                                menuOpen = false
                                actions.onEdit(message)
                            }
                        }
                        MenuItem("Delete", Icons.Default.Delete, destructive = true) {
                            menuOpen = false
                            actions.onDelete(message)
                        }
                    } else {
                        MenuItem("View profile", Icons.Default.Person) {
                            menuOpen = false
                            actions.onUserClicked(message.senderId)
                        }
                        if (showSender && !isGuest) {
                            MenuItem("Message privately", Icons.Default.Email) {
                                menuOpen = false
                                actions.onMessageUser(message.senderId, message.senderNickname)
                            }
                        }
                        if (!isGuest) {
                            MenuItem("Report", Icons.Default.Flag) {
                                menuOpen = false
                                onReport()
                            }
                            MenuItem("Block ${message.senderNickname.ifEmpty { "user" }}", Icons.Default.Block, destructive = true) {
                                menuOpen = false
                                onBlock()
                            }
                        }
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.sdp),
                    horizontalArrangement = Arrangement.spacedBy(4.sdp),
                ) {
                    REACTION_EMOJIS.filter { it in message.reactions }.forEach { emoji ->
                        val users = message.reactions[emoji].orEmpty()
                        val mine = currentUserId in users
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.sdp))
                                .background(if (mine) colors.neonCyan.copy(alpha = 0.18f) else colors.surfaceVariant)
                                .border(1.sdp, if (mine) colors.neonCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.sdp))
                                .clickable(enabled = canInteract) { actions.onReaction(message, emoji) }
                                .padding(horizontal = 7.sdp, vertical = 2.sdp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(emoji, fontSize = 12.ssp)
                            if (users.size > 1) {
                                Spacer(Modifier.width(3.sdp))
                                Text(users.size.toString(), style = MaterialTheme.typography.labelSmall.copy(color = colors.textPrimary))
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun TypingDots() {
    val colors = LocalSpeakMindColors.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.sdp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(6.sdp).background(colors.neonCyan.copy(alpha = alpha), CircleShape))
        }
    }
}

@Composable
private fun EmptyChat(uiState: RoomChatUiState) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.sdp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (uiState.kind == RoomKind.DIRECT) "👋" else "🌍", fontSize = 44.ssp)
        Spacer(Modifier.height(10.sdp))
        Text(
            when {
                uiState.isGuest -> "Sign in to join the conversation"
                uiState.kind == RoomKind.DIRECT -> "Say hello to ${uiState.title}"
                else -> "Be the first to say hi!"
            },
            style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
        )
        Text(
            when {
                uiState.isGuest -> "Messages from learners around the world will appear here."
                uiState.kind == RoomKind.DIRECT -> "Your messages are private between the two of you."
                else -> "Practise your English with learners around the world."
            },
            style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 4.sdp),
        )
    }
}

@Composable
private fun VoiceMessage(
    message: RoomMessage,
    playback: VoicePlaybackUi?,
    isLoading: Boolean,
    tint: Color,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeed: () -> Unit,
) {
    val isActive = playback != null
    val isPlaying = isActive && !playback!!.isPaused
    // Ticks arrive ~30×/s; a short linear animation hides the steps.
    val progress by animateFloatAsState(
        targetValue = playback?.progress ?: 0f,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "voiceProgress",
    )
    val levels = remember(message.id, message.voiceWaveform) {
        message.voiceWaveform.ifEmpty { placeholderWaveform(message.id) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.sdp),
        modifier = Modifier.width(220.sdp).padding(vertical = 2.sdp),
    ) {
        Box(
            modifier = Modifier
                .size(38.sdp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .then(if (!isLoading) Modifier.clickable(onClick = onPlayPause) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(18.sdp), color = tint, strokeWidth = 2.sdp)
                else -> Crossfade(targetState = isPlaying, label = "playPause") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play voice message",
                        tint = tint,
                        modifier = Modifier.size(24.sdp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Waveform(
                levels = levels,
                progress = if (isActive) progress else 0f,
                activeColor = tint,
                inactiveColor = tint.copy(alpha = 0.32f),
                onSeek = if (isLoading || message.isPending) null else onSeek,
                modifier = Modifier.fillMaxWidth().height(28.sdp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 3.sdp),
            ) {
                Text(
                    // Counts up while playing, like WhatsApp; otherwise the clip length.
                    text = formatDuration(if (isActive) playback!!.positionMs else message.voiceDurationMs),
                    style = MaterialTheme.typography.labelSmall.copy(color = tint.copy(alpha = 0.85f), fontSize = 10.ssp),
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(visible = isActive, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = formatSpeed(playback?.speed ?: 1f),
                        style = MaterialTheme.typography.labelSmall.copy(color = tint, fontWeight = FontWeight.Bold, fontSize = 10.ssp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.sdp))
                            .background(tint.copy(alpha = 0.16f))
                            .clickable(onClick = onSpeed)
                            .padding(horizontal = 8.sdp, vertical = 2.sdp),
                    )
                }
            }
        }
    }
}

@Composable
private fun JumpToNewestButton(unseen: Int, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Box {
        Box(
            modifier = Modifier
                .size(40.sdp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.25f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to newest messages", tint = colors.neonCyan)
        }
        if (unseen > 0) {
            Text(
                text = if (unseen > 99) "99+" else unseen.toString(),
                style = MaterialTheme.typography.labelSmall.copy(color = colors.backgroundDark, fontWeight = FontWeight.Bold, fontSize = 9.ssp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.sdp, y = (-4).sdp)
                    .defaultMinSize(minWidth = 18.sdp)
                    .background(colors.neonCyan, CircleShape)
                    .padding(horizontal = 5.sdp, vertical = 1.sdp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Placeholder bubbles while the first page loads (instead of a lone spinner). */
@Composable
private fun ChatSkeleton() {
    val colors = LocalSpeakMindColors.current
    val shimmer = rememberInfiniteTransition(label = "skeleton")
    val alpha by shimmer.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.sdp, vertical = 12.sdp),
        verticalArrangement = Arrangement.spacedBy(10.sdp, Alignment.Bottom),
    ) {
        listOf(0.55f to false, 0.4f to false, 0.62f to true, 0.35f to true, 0.5f to false, 0.45f to true).forEach { (width, mine) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                if (!mine) {
                    Box(Modifier.size(30.sdp).graphicsLayer { this.alpha = alpha }.background(colors.surfaceVariant, CircleShape))
                    Spacer(Modifier.width(8.sdp))
                }
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .height(38.sdp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(RoundedCornerShape(16.sdp))
                        .background(if (mine) colors.neonCyan.copy(alpha = 0.25f) else colors.surfaceVariant),
                )
            }
        }
    }
}

/** Bars of [levels] (0..1); the played part is drawn in [activeColor]. Tap or drag to seek. */
@Composable
private fun Waveform(
    levels: List<Float>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: progress
    val seekModifier = if (onSeek == null) Modifier else Modifier
        .pointerInput(onSeek) {
            detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
        }
        .pointerInput(onSeek) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { dragFraction?.let(onSeek); dragFraction = null },
                onDragCancel = { dragFraction = null },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                },
            )
        }
    Canvas(modifier = modifier.then(seekModifier)) {
        if (levels.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (levels.size - 1)) / levels.size).coerceAtLeast(1f)
        val minHeight = 3.dp.toPx()
        levels.forEachIndexed { i, level ->
            val x = i * (barWidth + gap)
            val h = (minHeight + (size.height - minHeight) * level).coerceAtMost(size.height)
            val barEnd = (x + barWidth) / size.width
            drawRoundRect(
                color = if (barEnd <= shown + 0.001f) activeColor else inactiveColor,
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** Older clips have no stored waveform: a stable made-up shape per message. */
private fun placeholderWaveform(seed: String, bars: Int = 48): List<Float> {
    val random = kotlin.random.Random(seed.hashCode())
    return List(bars) { 0.2f + random.nextFloat() * 0.65f }
}

private fun formatSpeed(speed: Float): String = when (speed) {
    1f -> "1×"
    2f -> "2×"
    else -> "${speed}×"
}

// ---------------- Composer ----------------

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
private fun ComposerContextBar(icon: ImageVector, title: String, text: String, onClose: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .padding(start = 14.sdp, end = 4.sdp, top = 6.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.sdp).height(34.sdp).background(colors.neonCyan, RoundedCornerShape(2.sdp)))
        Spacer(Modifier.width(8.sdp))
        Icon(icon, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(16.sdp))
        Column(Modifier.weight(1f).padding(horizontal = 8.sdp)) {
            Text(title, style = MaterialTheme.typography.labelSmall.copy(color = colors.neonCyan, fontWeight = FontWeight.Bold))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.textMuted)
        }
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
private fun SignInBar(onSignIn: () -> Unit, loading: Boolean) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = 16.sdp, vertical = 12.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("You're reading as a visitor.", style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted))
        Spacer(Modifier.height(8.sdp))
        GoogleSignInPill("Sign in with Google to chat", loading = loading, onClick = onSignIn)
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
private fun RecordingBar(elapsedMs: Long, levels: List<Float>, onCancel: () -> Unit, onSend: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val pulse = rememberInfiniteTransition(label = "recPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recDot",
    )
    val sendScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "recSend",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = 10.sdp, vertical = 8.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, contentDescription = "Discard recording", tint = colors.error)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(46.sdp)
                .clip(RoundedCornerShape(23.sdp))
                .background(colors.surfaceVariant.copy(alpha = 0.8f))
                .padding(horizontal = 14.sdp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp),
        ) {
            Box(Modifier.size(10.sdp).graphicsLayer { alpha = dotAlpha }.background(colors.error, CircleShape))
            Text(
                text = formatDuration(elapsedMs),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
            )
            LiveWaveform(levels = levels, color = colors.error.copy(alpha = 0.85f), modifier = Modifier.weight(1f).height(26.sdp))
        }
        Box(
            modifier = Modifier
                .size(48.sdp)
                .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.neonCyan, colors.neonCyanDark)))
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice message", tint = colors.backgroundDark, modifier = Modifier.size(20.sdp))
        }
    }
}

/** The newest input levels, right-aligned so the waveform grows from the right as you speak. */
@Composable
private fun LiveWaveform(levels: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val barWidth = 3.dp.toPx()
        val slots = ((size.width + gap) / (barWidth + gap)).toInt().coerceAtLeast(1)
        val shown = levels.takeLast(slots)
        val minHeight = 3.dp.toPx()
        shown.forEachIndexed { i, level ->
            val x = size.width - (shown.size - i) * (barWidth + gap) + gap
            val h = minHeight + (size.height - minHeight) * level
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun InputBar(
    /** Changes when Reply/Edit is chosen, to focus the field and open the keyboard. */
    focusKey: String?,
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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusKey) {
        if (focusKey != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = 10.sdp, vertical = 8.sdp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.sdp))
                .background(colors.surfaceVariant)
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.2f), RoundedCornerShape(24.sdp))
                .clickable { onFocusText() }
                .padding(start = 6.sdp, end = 14.sdp, top = 4.sdp, bottom = 4.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.sdp)
                    .clip(CircleShape)
                    .clickable { onToggleEmojis() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (showEmojis) "⌨️" else "😊", fontSize = 18.ssp)
            }
            BasicTextField(
                value = text,
                onValueChange = onInputChanged,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                ),
                maxLines = 5,
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 8.sdp)) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }
        val hasText = text.isNotBlank()
        val showMic = !hasText && allowVoice
        val enabled = hasText || showMic
        Box(
            modifier = Modifier
                .size(46.sdp)
                .clip(CircleShape)
                .background(
                    if (enabled) Brush.linearGradient(listOf(colors.neonCyan, colors.neonCyanDark))
                    else Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceVariant)),
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

