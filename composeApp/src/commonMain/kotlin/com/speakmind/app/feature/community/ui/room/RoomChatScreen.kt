package com.speakmind.app.feature.community.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.config.AppConfig
import com.speakmind.app.feature.community.data.model.MessageKind
import com.speakmind.app.feature.community.data.model.REACTION_EMOJIS
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.data.model.RoomMessage
import com.speakmind.app.feature.community.data.repository.ReportReason
import com.speakmind.app.feature.community.ui.auth.GoogleIdResult
import com.speakmind.app.feature.community.ui.auth.rememberGoogleSignIn
import com.speakmind.app.feature.community.ui.components.ChatIcons
import com.speakmind.app.feature.community.ui.components.ChatSheet
import com.speakmind.app.feature.community.ui.components.ConfirmSheet
import com.speakmind.app.feature.community.ui.components.DangerRed
import com.speakmind.app.feature.community.ui.components.GoogleSignInPill
import com.speakmind.app.feature.community.ui.components.OnlineDot
import com.speakmind.app.feature.community.ui.components.PremiumCrown
import com.speakmind.app.feature.community.ui.components.PremiumPill
import com.speakmind.app.feature.community.ui.components.ProfileSheet
import com.speakmind.app.feature.community.ui.components.ResizeWindowForKeyboard
import com.speakmind.app.feature.community.ui.components.SheetButton
import com.speakmind.app.feature.community.ui.components.SheetCheckRow
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.avatarColorFor
import com.speakmind.app.feature.community.ui.components.dayKey
import com.speakmind.app.feature.community.ui.components.formatClock
import com.speakmind.app.feature.community.ui.components.formatDayLabel
import com.speakmind.app.feature.community.ui.components.formatDuration
import com.speakmind.app.feature.community.ui.components.isPremiumMember
import com.speakmind.app.feature.community.ui.components.premiumNameColor
import com.speakmind.app.navigation.RoomDestination
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.brandFill
import com.speakmind.app.ui.components.onBrandFill
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

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
    val input = vm.input
    val onInputChanged = vm::onInputChanged
    val onSend = vm::onSendClicked
    val onFix = vm::onFixClicked
    val onUndoFix = vm::onUndoFix
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
    val onDeleteChat = vm::onDeleteChatRequested
    val onRetryConnection = vm::onRetryConnection
    val onDailyLimitDismissed = vm::onDailyLimitDismissed
    val onContentWarningDismissed = vm::onContentWarningDismissed
    val onErrorDismissed = vm::onErrorDismissed
    val onInfoDismissed = vm::onInfoDismissed
    val onReplyNotFound = vm::onReplyNotFound
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
                        viewModel.onMessageUserClicked(user.uid, user.nickname)
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
            ReportSheet(target = target, onDismiss = viewModel::onReportDismissed, onSubmit = viewModel::onSubmitReport)
        }
        uiState.blockTarget?.let { target ->
            ConfirmSheet(
                title = "Block ${target.nickname}?",
                message = "They won't be able to message you privately, and you won't see their messages in Speaky Hub. They won't be told.",
                confirmLabel = "Block",
                icon = ChatIcons.Block,
                onConfirm = viewModel::onConfirmBlock,
                onDismiss = viewModel::onBlockDismissed,
            )
        }
        if (uiState.confirmDeleteChat) {
            ConfirmSheet(
                title = "Delete this chat?",
                message = "It will be deleted for you only. ${uiState.otherUser?.nickname ?: "The other person"} will still see it. A new message brings the chat back.",
                confirmLabel = "Delete chat",
                icon = SpeakyIcons.Trash,
                onConfirm = viewModel::onConfirmDeleteChat,
                onDismiss = viewModel::onDeleteChatDismissed,
            )
        }
        uiState.deleteTarget?.let {
            ConfirmSheet(
                title = "Delete message?",
                message = "It will be removed for everyone.",
                confirmLabel = "Delete for everyone",
                icon = SpeakyIcons.Trash,
                onConfirm = viewModel::onConfirmDelete,
                onDismiss = viewModel::onDeleteDismissed,
            )
        }
    }
}

@Composable
private fun RoomChatContent(uiState: RoomChatUiState, actions: RoomActions) {
    val colors = LocalSpeakMindColors.current
    // Filtered once per change of messages or blocks, not on every recomposition.
    val messages = remember(uiState.messages, uiState.blockedUserIds, uiState.kind) { uiState.visibleMessages }
    val items = remember(messages) { buildItems(messages) }
    val listState = rememberLazyListState()
    var showEmojis by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDirect = uiState.kind == RoomKind.DIRECT

    // reverseLayout: index 0 is the newest message at the bottom, so a chat opens on its latest
    // messages instantly (no scroll through the history) and older pages append without jumps.
    val reversedItems = remember(items) { items.asReversed() }
    val scope = rememberCoroutineScope()
    val lastMessage = messages.lastOrNull()
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    // The newest message the user has seen at the bottom; the badge counts everything that arrived after it
    // (several messages can arrive in one update, e.g. after a reconnect, so a +1 per update undercounts).
    var seenNewestId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lastMessage?.id) {
        val newest = lastMessage ?: return@LaunchedEffect
        if (seenNewestId == null || atBottom || newest.senderId == uiState.currentUserId) {
            seenNewestId = newest.id
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(atBottom) { if (atBottom) seenNewestId = lastMessage?.id }
    val unseenBelow = remember(messages, seenNewestId) {
        val seenIndex = messages.indexOfLast { it.id == seenNewestId }
        if (seenIndex < 0) 0
        else messages.subList(seenIndex + 1, messages.size).count { it.senderId != uiState.currentUserId }
    }

    // Tapping a quoted message scrolls to the original (loading older pages if needed) and flashes it.
    var highlightId by remember { mutableStateOf<String?>(null) }
    val latestItems by rememberUpdatedState(reversedItems)
    val latestState by rememberUpdatedState(uiState)
    val jumpToMessage: (String) -> Unit = { id ->
        scope.launch {
            var pages = 0
            while (true) {
                val index = latestItems.indexOfFirst { it is ChatItem.Message && it.message.id == id }
                if (index >= 0) {
                    val typingRow = if (latestState.typingText != null && isDirect) 1 else 0
                    listState.animateScrollToItem(index + typingRow, -listState.layoutInfo.viewportSize.height / 3)
                    highlightId = id
                    kotlinx.coroutines.delay(1600)
                    if (highlightId == id) highlightId = null
                    return@launch
                }
                if (!latestState.hasMore || latestState.isOffline || pages++ >= 15) return@launch actions.onReplyNotFound()
                actions.onLoadMore()
                kotlinx.coroutines.delay(100)
                snapshotFlow { latestState.isLoadingMore }.first { !it }
            }
        }
    }

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
            .background(colors.backgroundDark)
            .imePadding(),
    ) {
        ChatHeader(uiState = uiState, actions = actions)
        if (uiState.kind == RoomKind.CHANNEL && !uiState.isGuest) {
            val navigation = org.koin.compose.koinInject<com.speakmind.app.navigation.NavigationManager>()
            val callManager = org.koin.compose.koinInject<com.speakmind.app.feature.community.call.HubCallManager>()
            LaunchedEffect(uiState.roomId) { if (uiState.roomId.isNotEmpty()) callManager.watch(uiState.roomId) }
            com.speakmind.app.feature.community.call.HubCallBanner(
                onOpenCall = { navigation.navigate(com.speakmind.app.feature.community.call.HubCallDestination) },
            )
        }

        if (uiState.isOffline) {
            Row(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.goldSoft)
                    .clickable { actions.onRetryConnection() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(ChatIcons.WifiOff, contentDescription = null, tint = colors.onGoldSoft, modifier = Modifier.size(18.dp))
                Text(
                    "No connection · showing saved messages. Tap to retry.",
                    color = colors.onGoldSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.isInitialLoading) {
                ChatSkeleton()
            } else if (messages.isEmpty()) {
                EmptyChat(uiState, onOpener = actions.onInputChanged)
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    if (uiState.typingText != null && isDirect) {
                        item(key = "typing_bubble") { TypingBubble(Modifier.padding(top = 8.dp)) }
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
                                        showSender = !isDirect,
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
                                            isDirect && message.createdAtMillis <= uiState.otherLastReadAt -> Tick.SEEN
                                            else -> Tick.SENT
                                        },
                                        currentUserId = uiState.currentUserId,
                                        canInteract = uiState.canPost,
                                        isGuest = uiState.isGuest,
                                        actions = actions,
                                        onReport = { actions.onReport(ReportTarget(message.senderId, nickname, message)) },
                                        onBlock = { actions.onBlock(ReportTarget(message.senderId, nickname)) },
                                        highlighted = highlightId == message.id,
                                        onReplyClick = jumpToMessage,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "top") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            if (uiState.isLoadingMore) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = colors.neonCyan, strokeWidth = 2.dp)
                            } else if (!uiState.hasMore && !uiState.isOffline) {
                                Text(
                                    if (isDirect) "This is the start of your conversation" else "Welcome to Speaky Hub 🌍",
                                    color = colors.faint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
            ) {
                JumpToNewestButton(unseen = unseenBelow, onClick = { scope.launch { listState.animateScrollToItem(0) } })
            }
        }

        // Hub: "Riham and Tuzo are typing…" in green; private chats show a typing bubble in the list.
        AnimatedVisibility(visible = uiState.typingText != null && !isDirect, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = uiState.typingText ?: "",
                color = colors.success,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
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
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                items(emojiPickerItems) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { actions.onInputChanged(actions.input.value + emoji) }
                            .padding(6.dp),
                    )
                }
            }
        }

        Banner(text = uiState.contentWarning ?: uiState.error, icon = ChatIcons.Alert, background = DangerRed)
        Banner(text = uiState.info, icon = SpeakyIcons.Check, background = colors.textPrimary, contentColor = colors.backgroundDark)

        when {
            uiState.isBanned -> NoticeCard("Your account is suspended for breaking community rules.")
            uiState.guestRestricted -> SignInCard(onSignIn = actions.onSignIn, loading = uiState.isSigningIn)
            isDirect && uiState.isDirectBlocked -> BlockedCard(
                blockedByMe = uiState.isOtherBlockedByMe,
                nickname = uiState.otherUser?.nickname ?: "this user",
                onUnblock = actions.onUnblockOther,
            )
            uiState.dailyLimitReached && !isDirect -> HubLimitCard(onDismiss = actions.onDailyLimitDismissed)
            uiState.isRecording -> RecordingBar(
                elapsedMs = uiState.recordingElapsedMs,
                levels = uiState.recordingLevels,
                onCancel = actions.onCancelRecording,
                onSend = actions.onSendRecording,
            )
            else -> Column(
                Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.replyingTo?.let { replying ->
                    ComposerContextBar(
                        icon = SpeakyIcons.Reply,
                        title = "Replying to ${replying.senderNickname.ifEmpty { "yourself" }.takeIf { replying.senderId != uiState.currentUserId } ?: "yourself"}",
                        text = if (replying.kind == MessageKind.VOICE) "🎤 Voice message" else replying.text,
                        onClose = actions.onCancelReply,
                    )
                }
                uiState.editingMessage?.let { editing ->
                    ComposerContextBar(
                        icon = SpeakyIcons.Pencil,
                        title = "Editing message",
                        text = editing.text,
                        onClose = actions.onCancelEdit,
                    )
                }
                uiState.correctionOriginal?.let { original ->
                    AiFixedBar(original = original, onUndo = actions.onUndoFix)
                }
                // Only this box listens to the typed text, so keystrokes don't redraw the messages.
                ObserveInput(actions.input) { inputText ->
                InputBar(
                    focusKey = uiState.replyingTo?.id ?: uiState.editingMessage?.id,
                    text = inputText,
                    placeholder = if (isDirect) "Message ${uiState.title}" else "Message Speaky Hub",
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
                    // Signed-in members only (the server needs the account), not while editing.
                    showFix = uiState.canPost && uiState.editingMessage == null,
                    isFixing = uiState.isCorrecting,
                    fixed = uiState.correctionOriginal != null,
                    onFix = actions.onFix,
                    onMic = {
                        showEmojis = false
                        actions.onMic()
                    },
                )
                }
            }
        }
    }
}

/** Its own recompose scope: the text changes on every keystroke, the rest of the chat doesn't. */
@Composable
private fun ObserveInput(input: kotlinx.coroutines.flow.StateFlow<String>, content: @Composable (String) -> Unit) {
    val text by input.collectAsStateWithLifecycle()
    content(text)
}

// ---------------- Header ----------------

@Composable
private fun ChatHeader(uiState: RoomChatUiState, actions: RoomActions) {
    val colors = LocalSpeakMindColors.current
    val other = uiState.otherUser
    val isDirect = uiState.kind == RoomKind.DIRECT
    com.speakmind.app.ui.components.TintStatusBar(if (isDirect) null else colors.brandFill)
    val shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
    val onHeader = if (isDirect) colors.textPrimary else colors.onBrandFill
    val onHeaderSoft = if (colors.isGold) colors.onBrandFill.copy(alpha = 0.8f) else Color(0xFFE4DEFF)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDirect) Modifier.shadow(8.dp, shape, ambientColor = Color(0x0D17142B), spotColor = Color(0x0D17142B)) else Modifier)
            .clip(shape)
            .background(if (isDirect) colors.surface else colors.brandFill)
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = actions.onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpeakyIcons.Back, contentDescription = "Back", tint = onHeader, modifier = Modifier.size(22.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                // Not clipped: the Premium ring, crown and glow draw just outside the photo.
                .then(if (other != null) Modifier.clickable { actions.onUserClicked(other.uid) } else Modifier)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val premium = isDirect && isPremiumMember(other?.uid)
            if (isDirect && other != null) {
                Box {
                    UserAvatar(
                        nickname = other.nickname,
                        photoUrl = other.photoUrl,
                        size = 44.dp,
                        borderColor = colors.neonCyan,
                        premium = premium,
                        medal = true,
                    )
                    OnlineDot(
                        online = uiState.isOtherOnline && !uiState.isDirectBlocked,
                        size = 12.dp,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp),
                    )
                }
            } else {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(onHeader.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SpeakyIcons.Globe, contentDescription = null, tint = onHeader, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = uiState.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (isDirect) TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) else displayStyle(18.sp),
                        color = if (premium) premiumNameColor() else onHeader,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (premium) PremiumPill(crown = false, fontSize = 10.sp)
                }
                val level = other?.level?.takeIf { it.isNotEmpty() }
                val (subtitle, subtitleColor) = when {
                    uiState.typingText != null -> uiState.typingText to (if (isDirect) colors.success else onHeaderSoft)
                    !isDirect -> (if (uiState.onlineCount > 0) "${uiState.onlineCount} online · general chat" else "General chat for all learners") to onHeaderSoft
                    uiState.isOtherBlockedByMe -> "Blocked" to colors.error
                    uiState.isOtherOnline -> listOfNotNull("Online", level).joinToString(" · ") to colors.success
                    else -> listOfNotNull("Private chat", level).joinToString(" · ") to colors.textMuted
                }
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = subtitleColor,
                    fontSize = 12.sp,
                    fontWeight = if (isDirect && subtitleColor != colors.textMuted) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        if (!uiState.isGuest && !isDirect && uiState.kind == RoomKind.CHANNEL) {
            val navigation = org.koin.compose.koinInject<com.speakmind.app.navigation.NavigationManager>()
            com.speakmind.app.feature.community.call.HubCallHeaderButton(
                tint = onHeader,
                onOpenCall = { navigation.navigate(com.speakmind.app.feature.community.call.HubCallDestination) },
            )
        }
        if (!uiState.isGuest && isDirect && other != null) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SpeakyIcons.Dots, contentDescription = "Chat options", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = colors.surface,
                    shadowElevation = 16.dp,
                    modifier = Modifier.widthIn(min = 230.dp).padding(horizontal = 6.dp),
                ) {
                    MenuItem("View profile", SpeakyIcons.Person) {
                        menuOpen = false
                        actions.onUserClicked(other.uid)
                    }
                    MenuItem(if (uiState.muted) "Unmute notifications" else "Mute notifications", SpeakyIcons.Bell) {
                        menuOpen = false
                        actions.onToggleMute()
                    }
                    MenuItem("Delete chat", SpeakyIcons.Trash, destructive = true) {
                        menuOpen = false
                        actions.onDeleteChat()
                    }
                    MenuDivider()
                    MenuItem("Report ${other.nickname}", ChatIcons.Flag, destructive = true) {
                        menuOpen = false
                        actions.onReport(ReportTarget(other.uid, other.nickname))
                    }
                    if (uiState.isOtherBlockedByMe) {
                        MenuItem("Unblock", ChatIcons.Block) {
                            menuOpen = false
                            actions.onUnblockOther()
                        }
                    } else {
                        MenuItem("Block", ChatIcons.Block, destructive = true) {
                            menuOpen = false
                            actions.onBlock(ReportTarget(other.uid, other.nickname))
                        }
                    }
                }
            }
        }
    }
}

/** 48dp menu row: 20dp stroke icon and a 15sp label; red for destructive actions. */
@Composable
private fun MenuItem(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val tint = if (destructive) colors.error else colors.textPrimary
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = if (destructive) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(LocalSpeakMindColors.current.track))
}

// ---------------- Messages ----------------

private enum class Tick { NONE, SENT, SEEN }

@Composable
private fun DaySeparator(label: String) {
    val colors = LocalSpeakMindColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
    highlighted: Boolean = false,
    onReplyClick: (String) -> Unit = {},
) {
    val colors = LocalSpeakMindColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val highlight by androidx.compose.animation.animateColorAsState(
        if (highlighted) colors.neonCyan.copy(alpha = 0.18f) else Color.Transparent,
        tween(350),
        label = "replyHighlight",
    )
    val avatarSize: Dp = 34.dp
    val showAvatarColumn = showSender && !isFromMe

    // Swipe right to reply (like WhatsApp): the bubble follows the finger, an arrow fades in,
    // and releasing past the threshold quotes the message in the composer.
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (firstInGroup) 10.dp else 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(highlight),
    ) {
        Icon(
            SpeakyIcons.Reply,
            contentDescription = null,
            tint = colors.neonCyan,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(22.dp)
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
            val senderPremium = isPremiumMember(message.senderId)
            if (showAvatarColumn) {
                if (lastInGroup) {
                    Box(Modifier.clip(CircleShape).clickable { actions.onUserClicked(message.senderId) }) {
                        UserAvatar(
                            nickname = message.senderNickname.ifEmpty { "?" },
                            photoUrl = message.senderPhotoUrl,
                            size = avatarSize,
                            borderColor = colors.neonCyan,
                            premium = senderPremium,
                        )
                    }
                } else {
                    Spacer(Modifier.width(avatarSize))
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
                modifier = Modifier.fillMaxWidth(if (showAvatarColumn) 0.82f else 0.78f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (showAvatarColumn && firstInGroup && message.senderNickname.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { actions.onUserClicked(message.senderId) }
                            .padding(start = 6.dp),
                    ) {
                        Text(
                            text = message.senderNickname,
                            color = if (senderPremium) premiumNameColor() else colors.neonCyanDark,
                            fontSize = 12.sp,
                            fontWeight = if (senderPremium) FontWeight.ExtraBold else FontWeight.Bold,
                        )
                        if (senderPremium) PremiumCrown(12.dp)
                    }
                }
                val big = if (showSender) 18.dp else 20.dp
                val tail = 6.dp
                val shape = if (isFromMe) {
                    RoundedCornerShape(topStart = big, topEnd = if (firstInGroup) big else tail, bottomStart = big, bottomEnd = tail)
                } else {
                    RoundedCornerShape(topStart = if (firstInGroup) big else tail, topEnd = big, bottomStart = tail, bottomEnd = big)
                }
                // Premium members stand out in the Hub with a warm gold bubble.
                val goldBubble = showSender && !isFromMe && senderPremium
                val dark = colors.surface.luminance() < 0.5f
                val contentColor = if (isFromMe) colors.onBubble else colors.textPrimary
                val metaColor = if (isFromMe) colors.onBubble.copy(alpha = 0.75f) else colors.textMuted
                Box {
                    Column(
                        modifier = Modifier
                            .clip(shape)
                            .then(
                                when {
                                    isFromMe -> Modifier.background(colors.userBubbleGradient)
                                    goldBubble -> Modifier
                                        .background(if (dark) colors.goldSoft else Color(0xFFFFF8E6))
                                        .border(1.5.dp, if (dark) colors.gold.copy(alpha = 0.5f) else Color(0xFFF5D27A), shape)
                                    else -> Modifier.background(colors.surface)
                                }
                            )
                            .combinedClickable(
                                onClick = { if (message.isFailed) actions.onRetry(message.id) },
                                onLongClick = { if (!message.isPending) menuOpen = true },
                            )
                            .padding(
                                start = if (message.replyTo != null) 8.dp else 12.dp,
                                end = if (message.replyTo != null) 8.dp else 12.dp,
                                top = if (message.replyTo != null) 8.dp else 9.dp,
                                bottom = 8.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.replyTo?.let { reply ->
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isFromMe) colors.onBubble.copy(alpha = 0.16f) else colors.primarySoft.copy(alpha = 0.7f))
                                    .clickable { onReplyClick(reply.messageId) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    reply.senderNickname.ifEmpty { "Reply" },
                                    color = if (isFromMe) colors.onBubble else colors.neonCyanDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    when {
                                        reply.isVoice -> "🎤 Voice message"
                                        reply.text.isEmpty() -> "Original message"
                                        else -> reply.text
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isFromMe) colors.onBubble.copy(alpha = 0.85f) else colors.textSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        val textPad = if (message.replyTo != null) Modifier.padding(horizontal = 4.dp) else Modifier
                        when {
                            message.isVoiceExpired -> Text(
                                "🎤 Voice message expired",
                                color = metaColor,
                                fontSize = 15.sp,
                                fontStyle = FontStyle.Italic,
                                modifier = textPad,
                            )
                            message.kind == MessageKind.VOICE -> VoiceMessage(
                                message = message,
                                playback = playback,
                                isLoading = isLoadingVoice || message.isPending,
                                accent = if (isFromMe) colors.onBubble else avatarColorFor(message.senderNickname),
                                onAccent = if (isFromMe) colors.brandFill else Color.White,
                                metaColor = metaColor,
                                onPlayPause = { actions.onPlayVoice(message) },
                                onSeek = { actions.onSeekVoice(message, it) },
                                onSpeed = actions.onCycleVoiceSpeed,
                            )
                            else -> Text(message.text, color = contentColor, fontSize = 15.sp, lineHeight = 22.sp, modifier = textPad)
                        }
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            when {
                                message.isFailed -> {
                                    Icon(ChatIcons.Alert, null, tint = if (isFromMe) colors.onBubble else colors.error, modifier = Modifier.size(12.dp))
                                    Text("Not sent · tap to retry", color = if (isFromMe) colors.onBubble else colors.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                message.isPending -> Icon(ChatIcons.Clock, "Sending", tint = metaColor, modifier = Modifier.size(12.dp))
                                else -> {
                                    Text(
                                        (if (message.isEdited) "edited · " else "") + formatClock(message.createdAtMillis),
                                        color = metaColor,
                                        fontSize = 11.sp,
                                    )
                                    when (tick) {
                                        Tick.SENT -> Icon(SpeakyIcons.Check, "Sent", tint = metaColor, modifier = Modifier.size(12.dp))
                                        Tick.SEEN -> Icon(ChatIcons.Seen, "Seen", tint = colors.onBubble, modifier = Modifier.size(width = 16.dp, height = 12.dp))
                                        Tick.NONE -> Unit
                                    }
                                }
                            }
                        }
                    }
                }

                if (message.reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .offset(y = (-8).dp)
                            .padding(start = if (isFromMe) 0.dp else 8.dp, end = if (isFromMe) 10.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        REACTION_EMOJIS.filter { it in message.reactions }.forEach { emoji ->
                            val users = message.reactions[emoji].orEmpty()
                            val mine = currentUserId in users
                            Row(
                                modifier = Modifier
                                    .shadow(3.dp, RoundedCornerShape(999.dp), ambientColor = Color(0x1F17142B), spotColor = Color(0x1F17142B))
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (mine) colors.primarySoft else colors.surface)
                                    .clickable(enabled = canInteract) { actions.onReaction(message, emoji) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(emoji, fontSize = 12.sp)
                                Text(users.size.toString(), color = if (mine) colors.onPrimarySoft else colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (menuOpen) {
        MessageMenu(
            message = message,
            isFromMe = isFromMe,
            showSender = showSender,
            currentUserId = currentUserId,
            canInteract = canInteract,
            isGuest = isGuest,
            actions = actions,
            onReport = onReport,
            onBlock = onBlock,
            onDismiss = { menuOpen = false },
        )
    }
}

/** Long-press menu: the message lifted over a dimmed chat, reactions above it and actions below. */
@Composable
private fun MessageMenu(
    message: RoomMessage,
    isFromMe: Boolean,
    showSender: Boolean,
    currentUserId: String,
    canInteract: Boolean,
    isGuest: Boolean,
    actions: RoomActions,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val clipboard = LocalClipboardManager.current
    val run: (() -> Unit) -> Unit = { action -> onDismiss(); action() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canInteract) {
                    Row(
                        Modifier
                            .shadow(20.dp, RoundedCornerShape(999.dp), ambientColor = Color(0x3317142B), spotColor = Color(0x3317142B))
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.surface)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        REACTION_EMOJIS.forEach { emoji ->
                            val mine = currentUserId in message.reactions[emoji].orEmpty()
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(if (mine) colors.primarySoft else Color.Transparent)
                                    .clickable { run { actions.onReaction(message, emoji) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
                Text(
                    when {
                        message.kind == MessageKind.VOICE -> "🎤 Voice message"
                        else -> message.text
                    },
                    color = if (isFromMe) colors.onBubble else colors.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .wrapContentWidth(if (isFromMe) Alignment.End else Alignment.Start)
                        .shadow(20.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x4017142B), spotColor = Color(0x4017142B))
                        .clip(if (isFromMe) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                        .background(if (isFromMe) colors.userBubbleGradient else SolidColor(colors.surface))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Column(
                    Modifier
                        .width(230.dp)
                        .shadow(20.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x3317142B), spotColor = Color(0x3317142B))
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(6.dp),
                ) {
                    if (canInteract) MenuItem("Reply", SpeakyIcons.Reply) { run { actions.onReply(message) } }
                    if (message.kind == MessageKind.TEXT) {
                        MenuItem("Copy", SpeakyIcons.Copy) { run { clipboard.setText(AnnotatedString(message.text)) } }
                    }
                    if (isFromMe) {
                        if (message.kind == MessageKind.TEXT) MenuItem("Edit", SpeakyIcons.Pencil) { run { actions.onEdit(message) } }
                        MenuDivider()
                        MenuItem("Delete for everyone", SpeakyIcons.Trash, destructive = true) { run { actions.onDelete(message) } }
                    } else {
                        MenuItem("View profile", SpeakyIcons.Person) { run { actions.onUserClicked(message.senderId) } }
                        if (showSender && !isGuest) {
                            MenuItem("Message privately", SpeakyIcons.Chat) {
                                run { actions.onMessageUser(message.senderId, message.senderNickname) }
                            }
                        }
                        if (!isGuest) {
                            MenuDivider()
                            MenuItem("Report", ChatIcons.Flag, destructive = true) { run(onReport) }
                            MenuItem("Block ${message.senderNickname.ifEmpty { "user" }}", ChatIcons.Block, destructive = true) { run(onBlock) }
                        }
                    }
                }
            }
        }
    }
}

/** Three grey dots in a white bubble while the other person types (private chats). */
@Composable
private fun TypingBubble(modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = "Typing" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 180), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).graphicsLayer { this.alpha = alpha }.background(colors.textMuted, CircleShape))
        }
    }
}

/**
 * Nothing posted yet. Private chat: big avatar, "Say hello" and three tap-to-use openers (they fill
 * the message box). Hub: a globe and a welcome line.
 */
@Composable
private fun EmptyChat(uiState: RoomChatUiState, onOpener: (String) -> Unit) {
    val colors = LocalSpeakMindColors.current
    val isDirect = uiState.kind == RoomKind.DIRECT
    val other = uiState.otherUser
    val firstName = uiState.title.substringBefore(' ').ifEmpty { uiState.title }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isDirect && other != null) {
            Box(Modifier.size(112.dp).background(colors.mintSoft, CircleShape), contentAlignment = Alignment.Center) {
                UserAvatar(nickname = other.nickname, photoUrl = other.photoUrl, size = 92.dp, borderColor = Color.Transparent)
            }
        } else {
            Box(Modifier.size(112.dp).background(colors.primarySoft, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(92.dp).background(colors.brandFill, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(SpeakyIcons.Globe, null, tint = colors.onBrandFill, modifier = Modifier.size(40.dp))
                }
            }
        }
        Text(
            when {
                uiState.isGuest -> "Sign in to join the conversation"
                isDirect -> "Say hello to $firstName"
                else -> "Be the first to say hi!"
            },
            style = displayStyle(24.sp),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            when {
                uiState.isGuest -> "Messages from learners around the world will appear here."
                isDirect -> "Practise your English together. Be kind and stay on topic."
                else -> "Practise your English with learners around the world."
            },
            color = colors.textSecondary,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
        )
        if (isDirect && uiState.canPost) {
            Column(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hi $firstName! Where are you from?", "Why are you learning English?", "Can we practise speaking?").forEach { opener ->
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.primarySoft, RoundedCornerShape(16.dp))
                            .clickable { onOpener(opener) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(opener, color = colors.onPrimarySoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceMessage(
    message: RoomMessage,
    playback: VoicePlaybackUi?,
    isLoading: Boolean,
    accent: Color,
    onAccent: Color,
    metaColor: Color,
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.width(216.dp).padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent)
                .then(if (!isLoading) Modifier.clickable(onClick = onPlayPause) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = onAccent, strokeWidth = 2.dp)
                else -> Crossfade(targetState = isPlaying, label = "playPause") { playing ->
                    Icon(
                        imageVector = if (playing) ChatIcons.Pause else SpeakyIcons.Play,
                        contentDescription = if (playing) "Pause" else "Play voice message",
                        tint = onAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Waveform(
            levels = levels,
            progress = if (isActive) progress else 0f,
            activeColor = accent,
            inactiveColor = accent.copy(alpha = 0.3f),
            onSeek = if (isLoading || message.isPending) null else onSeek,
            modifier = Modifier.weight(1f).height(30.dp),
        )
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                // Counts up while playing, like WhatsApp; otherwise the clip length.
                text = formatDuration(if (isActive) playback!!.positionMs else message.voiceDurationMs),
                color = metaColor,
                fontSize = 12.sp,
            )
            AnimatedVisibility(visible = isActive, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = formatSpeed(playback?.speed ?: 1f),
                    color = metaColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(metaColor.copy(alpha = 0.15f))
                        .clickable(onClick = onSpeed)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
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
                .size(42.dp)
                .shadow(8.dp, CircleShape, ambientColor = Color(0x2617142B), spotColor = Color(0x2617142B))
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ChatIcons.ArrowDown, contentDescription = "Jump to newest messages", tint = colors.neonCyan, modifier = Modifier.size(20.dp))
        }
        if (unseen > 0) {
            Text(
                text = if (unseen > 99) "99+" else unseen.toString(),
                color = colors.onBubble,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 20.dp)
                    .background(colors.userBubbleGradient, RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
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
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        listOf(0.55f to false, 0.4f to false, 0.62f to true, 0.35f to true, 0.5f to false, 0.45f to true).forEach { (width, mine) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .height(42.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                        .background(if (mine) colors.primarySoft else colors.surface),
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
        // The design's 3dp bars with 2dp gaps; resample the stored levels to fit the width.
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val count = ((size.width + gap) / (barWidth + gap)).toInt().coerceAtLeast(1)
        val minHeight = 4.dp.toPx()
        for (i in 0 until count) {
            val level = levels[(i * levels.size / count).coerceAtMost(levels.size - 1)]
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

/** Reply / edit preview above the message box. */
@Composable
private fun ComposerContextBar(icon: ImageVector, title: String, text: String, onClose: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.neonCyanDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.textMuted, fontSize = 12.sp)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(SpeakyIcons.Close, contentDescription = "Cancel", tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** "Fixed by AI" tray: the original crossed out, and Undo. */
@Composable
private fun AiFixedBar(original: String, onUndo: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.primarySoft).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SpeakyIcons.Sparkle, null, tint = colors.neonCyan, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Fixed by AI", color = colors.onPrimarySoft, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                original,
                color = colors.textSecondary,
                fontSize = 12.sp,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable(onClick = onUndo).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Undo", color = colors.onPrimarySoft, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Banner(text: String?, icon: ImageVector, background: Color, contentColor: Color = Color.White) {
    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Text(
                text = text ?: "",
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** White rounded card that replaces the composer (blocked, suspended, visitor). */
@Composable
private fun BottomCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun NoticeCard(text: String) {
    val colors = LocalSpeakMindColors.current
    BottomCard {
        Text(text, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit, loading: Boolean) {
    val colors = LocalSpeakMindColors.current
    BottomCard {
        Text("You're reading as a visitor.", color = colors.textSecondary, fontSize = 14.sp)
        GoogleSignInPill("Sign in with Google to chat", loading = loading, onClick = onSignIn)
    }
}

@Composable
private fun BlockedCard(blockedByMe: Boolean, nickname: String, onUnblock: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    BottomCard {
        Text(
            if (blockedByMe) "You can't send or receive messages from $nickname." else "You can't reply to this conversation.",
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        if (blockedByMe) {
            SheetButton("Unblock", colors.surfaceVariant, colors.textPrimary, 48, onUnblock)
        }
    }
}

/** Speaky Hub's daily limit reached: a dark card in place of the composer; tap to dismiss. */
@Composable
private fun HubLimitCard(onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.textPrimary)
            .clickable(onClick = onDismiss)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(ChatIcons.Moon, null, tint = Color(0xFFF5C94A), modifier = Modifier.size(34.dp))
        Text("That's all for today in Speaky Hub", style = displayStyle(20.sp), color = colors.backgroundDark, textAlign = TextAlign.Center)
        Text(
            "You can post again tomorrow, and private chats still work.",
            color = colors.backgroundDark.copy(alpha = 0.75f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Report a message or a person: reason cards, an optional note and "Also block". */
@Composable
private fun ReportSheet(
    target: ReportTarget,
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String, Boolean) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var details by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }
    ChatSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (target.message != null) "Report message" else "Report ${target.nickname}",
                    style = displayStyle(22.sp),
                    color = colors.textPrimary,
                )
                Text("What's wrong? Our moderators will review it.", color = colors.textSecondary, fontSize = 14.sp)
            }
            target.message?.takeIf { it.kind == MessageKind.TEXT }?.let {
                Text(
                    "“${it.text.take(140)}”",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.backgroundDark).padding(12.dp),
                )
            }
            ReportReason.entries.forEach { r ->
                val on = reason == r
                Row(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) DangerRed.copy(alpha = 0.08f) else colors.surface)
                        .border(2.dp, if (on) DangerRed else colors.track, RoundedCornerShape(14.dp))
                        .clickable { reason = r }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(20.dp).border(if (on) 6.dp else 2.dp, if (on) DangerRed else colors.faint.copy(alpha = 0.6f), CircleShape),
                    )
                    Text(r.label, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Add a note (optional)", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = details,
                onValueChange = { if (it.length <= 500) details = it },
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.neonCyan),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(2.dp, colors.track, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (details.isEmpty()) Text("Tell us more…", color = colors.faint, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            SheetCheckRow("Also block ${target.nickname}", alsoBlock) { alsoBlock = !alsoBlock }
            SheetButton("Send report", DangerRed, Color.White, 54, { reason?.let { onSubmit(it, details, alsoBlock) } }, enabled = reason != null)
        }
    }
}

/** Recording a voice message: red dot, timer and live waveform; trash discards, the red button sends. */
@Composable
private fun RecordingBar(elapsedMs: Long, levels: List<Float>, onCancel: () -> Unit, onSend: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val pulse = rememberInfiniteTransition(label = "recPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "recDot",
    )
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "recRing",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(10.dp).graphicsLayer { alpha = dotAlpha }.background(DangerRed, CircleShape))
            Text(
                text = formatDuration(elapsedMs),
                style = TextStyle(color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            )
            LiveWaveform(levels = levels, color = DangerRed.copy(alpha = 0.85f), modifier = Modifier.weight(1f).height(30.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpeakyIcons.Trash, contentDescription = "Discard recording", tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1f + ring * 0.45f
                    scaleY = 1f + ring * 0.45f
                    alpha = (1f - ring) * 0.5f
                }.background(DangerRed, CircleShape),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(DangerRed)
                    .clickable(role = Role.Button) { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpeakyIcons.Send, contentDescription = "Send voice message", tint = Color.White, modifier = Modifier.size(26.dp))
            }
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
    showFix: Boolean = false,
    isFixing: Boolean = false,
    /** The text was just fixed by AI: the field gets a violet edge, as in the design. */
    fixed: Boolean = false,
    onFix: () -> Unit = {},
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val fieldShape = RoundedCornerShape(26.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .shadow(6.dp, fieldShape, ambientColor = Color(0x1017142B), spotColor = Color(0x1017142B))
                .clip(fieldShape)
                .background(colors.surface)
                .then(if (fixed) Modifier.border(2.dp, colors.neonCyan, fieldShape) else Modifier)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFocusText() }
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onToggleEmojis() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (showEmojis) ChatIcons.Keyboard else SpeakyIcons.Smile,
                    contentDescription = if (showEmojis) "Keyboard" else "Emoji",
                    tint = if (showEmojis) colors.neonCyan else colors.textMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            // Text set from outside (Edit, AI fix, an opener) puts the cursor at the end, not the start.
            var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text)) }
            val fieldValue = if (field.text == text) field
            else androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))
            BasicTextField(
                value = fieldValue,
                onValueChange = {
                    field = it
                    if (it.text != text) onInputChanged(it.text)
                },
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(colors.neonCyan),
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                ),
                maxLines = 5,
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 8.dp)) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.faint,
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            // ✨ AI: fix the English, or translate to English from any language.
            if (showFix) {
                val canFix = text.isNotBlank() && !isFixing
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primarySoft)
                        .clickable(enabled = canFix, onClick = onFix),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isFixing) {
                        CircularProgressIndicator(color = colors.neonCyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(
                            SpeakyIcons.Sparkle,
                            contentDescription = "Fix my English with AI",
                            tint = colors.neonCyan.copy(alpha = if (text.isNotBlank()) 1f else 0.55f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        val hasText = text.isNotBlank()
        val showMic = !hasText && allowVoice
        val enabled = hasText || showMic
        Box(
            modifier = Modifier
                .size(52.dp)
                .then(
                    if (enabled) Modifier.shadow(10.dp, CircleShape, ambientColor = Color(0x595B3DF5), spotColor = Color(0x595B3DF5))
                    else Modifier
                )
                .clip(CircleShape)
                .background(if (enabled) colors.userBubbleGradient else SolidColor(colors.surfaceVariant))
                .then(if (enabled) Modifier.clickable(role = Role.Button) { if (hasText) onSend() else onMic() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (showMic) SpeakyIcons.Mic else SpeakyIcons.Send,
                contentDescription = if (showMic) "Record voice message" else "Send",
                tint = if (enabled) colors.onBubble else colors.textMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
