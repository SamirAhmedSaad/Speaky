package com.speakmind.app.feature.community.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.community.data.model.AdminChat
import com.speakmind.app.feature.community.data.model.AdminChatMessage
import com.speakmind.app.feature.community.ui.components.CommunityEmptyState
import com.speakmind.app.feature.community.ui.components.CommunityLoading
import com.speakmind.app.feature.community.ui.components.CommunityPageHeader
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.formatClock
import com.speakmind.app.feature.community.ui.components.formatDuration
import com.speakmind.app.feature.community.ui.components.formatListTime
import com.speakmind.app.navigation.AdminChatDestination
import com.speakmind.app.navigation.AdminChatsDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.CircleShape
import com.speakmind.app.feature.community.ui.components.CommunityToast
import com.speakmind.app.feature.community.ui.components.ConfirmSheet
import com.speakmind.app.feature.community.ui.components.SheetCheckRow
import com.speakmind.app.ui.components.SpeakyIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.adminChatsScreens() {
    animatedComposable<AdminChatsDestination> {
        val viewModel = koinViewModel<AdminChatsViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AdminChatsContent(uiState, viewModel)
    }
    animatedComposable<AdminChatDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<AdminChatDestination>()
        val viewModel = koinViewModel<AdminChatViewModel>(key = dest.roomId) { parametersOf(dest) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AdminChatContent(uiState, viewModel)
    }
}

/** Today: just the time; older: date and time. */
private fun whenText(millis: Long): String {
    val day = formatListTime(millis)
    val clock = formatClock(millis)
    return if (day == clock) clock else "$day $clock"
}

@Composable
private fun AdminChatsContent(uiState: AdminChatsUiState, viewModel: AdminChatsViewModel) {
    val colors = LocalSpeakMindColors.current
    Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
        Column(Modifier.fillMaxSize()) {
            CommunityPageHeader(title = "Private chats", subtitle = "Moderation · read only", onBack = viewModel::onBack)
            when {
                uiState.isLoading && uiState.chats.isEmpty() -> CommunityLoading()
                uiState.error != null && uiState.chats.isEmpty() -> CommunityEmptyState("⚠️", "Couldn't load", uiState.error)
                uiState.chats.isEmpty() -> CommunityEmptyState("💬", "No private chats", "Nobody has sent a private message yet.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(uiState.chats, key = { _, chat -> chat.roomId }) { index, chat ->
                        if (index >= uiState.chats.size - 5) LaunchedEffect(uiState.chats.size) { viewModel.loadMore() }
                        AdminChatRow(chat) { viewModel.onChatClicked(chat) }
                    }
                    if (uiState.isLoadingMore) item(key = "more_chats") { CommunityLoading() }
                }
            }
        }
    }
}

@Composable
private fun AdminChatRow(chat: AdminChat, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Both people, overlapping; each cut out of the card with a surface ring.
        Box(Modifier.width(60.dp).height(44.dp)) {
            PairAvatar(chat.userA.nickname, chat.userA.photoUrl, Modifier.align(Alignment.TopStart))
            PairAvatar(chat.userB?.nickname ?: "?", chat.userB?.photoUrl, Modifier.align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${chat.userA.nickname} ↔ ${chat.userB?.nickname ?: "Deleted account"}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                chat.lastMessagePreview.ifEmpty { "—" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.textMuted,
                fontSize = 13.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(whenText(chat.lastMessageAtMillis), color = colors.textMuted, fontSize = 11.sp)
            Text("${chat.messageCount} msg", color = colors.neonCyanDark, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PairAvatar(nickname: String, photoUrl: String?, modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    Box(modifier.size(38.dp).background(colors.surface, CircleShape).padding(2.5.dp)) {
        UserAvatar(nickname, photoUrl, 33.dp, colors.neonCyan)
    }
}

@Composable
private fun AdminChatContent(uiState: AdminChatUiState, viewModel: AdminChatViewModel) {
    val colors = LocalSpeakMindColors.current
    val dest = viewModel.destination
    LaunchedEffect(uiState.info, uiState.error) {
        if (uiState.info != null || (uiState.error != null && uiState.messages.isNotEmpty())) {
            kotlinx.coroutines.delay(3_000)
            viewModel.onMessageDismissed()
        }
    }
    Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
        Column(Modifier.fillMaxSize()) {
            CommunityPageHeader(
                title = "${dest.userAName} ↔ ${dest.userBName}",
                subtitle = "Admin review · delete, ban, play voice",
                titleSize = 18,
                onBack = viewModel::onBack,
            )
            // Ban either person straight from the conversation.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BanChip(dest.userAName, Modifier.weight(1f)) { viewModel.onBanRequested(dest.userAId, dest.userAName) }
                dest.userBId?.let { id ->
                    BanChip(dest.userBName, Modifier.weight(1f)) { viewModel.onBanRequested(id, dest.userBName) }
                }
            }
            when {
                uiState.isLoading -> CommunityLoading()
                uiState.error != null && uiState.messages.isEmpty() -> CommunityEmptyState("⚠️", "Couldn't load", uiState.error)
                uiState.messages.isEmpty() -> CommunityEmptyState("💬", "No messages", "This chat is empty.")
                else -> LazyColumn(
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(uiState.messages, key = { _, m -> m.id }) { index, message ->
                        if (index >= uiState.messages.size - 10) LaunchedEffect(uiState.messages.size) { viewModel.loadMore() }
                        // Person A on the left, person B on the right.
                        val fromA = message.senderId == dest.userAId
                        AdminMessageBubble(
                            message, if (fromA) dest.userAName else dest.userBName, fromA,
                            playing = uiState.playingId == message.id,
                            loading = uiState.loadingVoiceId == message.id,
                            onPlay = { viewModel.onPlayVoice(message) },
                            onDelete = { viewModel.onDeleteRequested(message) },
                        )
                    }
                    if (uiState.isLoadingMore) item(key = "older") { CommunityLoading() }
                }
            }
        }
        (uiState.error?.takeIf { uiState.messages.isNotEmpty() } ?: uiState.info)?.let { msg ->
            CommunityToast(msg, isError = uiState.error != null, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    uiState.deleteTarget?.let {
        ConfirmSheet(
            title = "Delete message?",
            message = "It will be removed for both people.",
            confirmLabel = "Delete",
            icon = SpeakyIcons.Trash,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDeleteCancelled,
        )
    }

    uiState.banTarget?.let { (_, name) ->
        var removeMessages by remember { mutableStateOf(false) }
        ConfirmSheet(
            title = "Ban $name?",
            message = "They won't be able to post or send private messages.",
            confirmLabel = "Ban user",
            icon = SpeakyIcons.Shield,
            onConfirm = { viewModel.onConfirmBan(removeMessages) },
            onDismiss = viewModel::onBanCancelled,
            extra = { SheetCheckRow("Also remove all their messages", removeMessages) { removeMessages = !removeMessages } },
        )
    }
}

@Composable
private fun BanChip(name: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.error.copy(alpha = 0.08f))
            .border(1.5.dp, colors.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Ban $name", maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** A is mint on the left, B pink on the right, so the two sides never get mixed up. */
@Composable
private fun AdminMessageBubble(
    message: AdminChatMessage,
    senderName: String,
    fromA: Boolean,
    playing: Boolean,
    loading: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val dark = colors.surface.luminance() < 0.5f
    val background = if (fromA) colors.mintSoft else if (dark) colors.coralSoft else Color(0xFFFCE4EF)
    val edge = if (fromA) colors.onMintSoft.copy(alpha = 0.25f) else if (dark) colors.coral.copy(alpha = 0.3f) else Color(0xFFF4B8D3)
    val nameColor = if (fromA) colors.onMintSoft else if (dark) colors.coral else Color(0xFF9D174D)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromA) Alignment.Start else Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .wrapContentWidth(if (fromA) Alignment.Start else Alignment.End)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .border(1.5.dp, edge, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(senderName, color = nameColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Delete", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = colors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (message.isVoice) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onPlay).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(nameColor), contentAlignment = Alignment.Center) {
                        Text(if (loading) "…" else if (playing) "■" else "▶", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        "Voice message · ${formatDuration(message.voiceDurationMs)}",
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                    )
                }
            } else {
                Text(message.text, color = colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
            }
            Text(
                whenText(message.createdAtMillis) + if (message.isEdited) " · edited" else "",
                color = colors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
