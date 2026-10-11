package com.speakmind.app.feature.community.ui.hub

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.ui.components.CommunityEmptyState
import com.speakmind.app.feature.community.ui.components.CommunityLoading
import com.speakmind.app.feature.community.ui.components.CommunityPageHeader
import com.speakmind.app.feature.community.ui.components.PersonCard
import com.speakmind.app.feature.community.ui.components.SectionTitle
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.rememberImagePickerLauncher
import com.speakmind.app.feature.community.ui.components.formatListTime
import com.speakmind.app.navigation.CommunityHubDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.communityHubScreen() {
    animatedComposable<CommunityHubDestination> {
        val viewModel = koinViewModel<CommunityHubViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleStartEffect(viewModel) {
            viewModel.onVisibilityChanged(true)
            onStopOrDispose { viewModel.onVisibilityChanged(false) }
        }
        LifecycleResumeEffect(viewModel) {
            viewModel.onResume()
            onPauseOrDispose { }
        }
        InboxContent(uiState = uiState, viewModel = viewModel)
    }
}

@Composable
private fun InboxContent(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    Box(Modifier.fillMaxSize().background(colors.backgroundGradient)) {
        Column(Modifier.fillMaxSize()) {
            CommunityPageHeader(
                title = "Chats",
                subtitle = when {
                    uiState.unreadTotal > 0 -> "${uiState.unreadTotal} unread"
                    else -> "${uiState.onlineUserIds.size} learners online"
                },
                onBack = viewModel::onBack,
                actions = {
                    PeopleButton(onClick = viewModel::openPeople)
                    if (!uiState.isGuest) AccountMenu(viewModel, uiState.isAdmin)
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.sdp, end = 20.sdp, top = 4.sdp, bottom = 32.sdp),
                verticalArrangement = Arrangement.spacedBy(10.sdp),
            ) {
                item(key = "hub") {
                    SpeakyHubCard(
                        hub = uiState.hubRoom,
                        onlineCount = uiState.onlineUserIds.size,
                        onClick = viewModel::openSpeakyHub,
                    )
                }
                if (uiState.isGuest) {
                    item(key = "signin") { SignInCard(onSignIn = viewModel::onSignIn) }
                } else {
                    item(key = "private_title") {
                        SectionTitle("Private messages", Modifier.padding(top = 14.sdp, bottom = 2.sdp))
                    }
                    when {
                        uiState.isLoadingChats && uiState.chats.isEmpty() -> item { CommunityLoading() }
                        uiState.chats.isEmpty() -> item {
                            CommunityEmptyState(
                                emoji = "💌",
                                title = "No private messages yet",
                                message = "Tap someone's name in Speaky Hub, or the people icon above, to start a chat.",
                            )
                        }
                    }
                    items(uiState.chats, key = { it.id }) { chat -> PrivateChatCard(chat, uiState, viewModel) }
                }
            }
        }

        uiState.error?.let { error ->
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3_500)
                viewModel.onErrorDismissed()
            }
            Text(
                text = error,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .background(Color(0xFFB71C1C).copy(alpha = 0.92f))
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            )
        }
        if (uiState.isDeletingAccount) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
        }
    }

    if (uiState.showEditProfile) {
        EditProfileDialog(uiState, viewModel)
    }
    if (uiState.confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteAccountDismissed,
            title = { Text("Delete your account?") },
            text = { Text("Your community profile, messages, voice notes and private chats are permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDeleteAccount) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::onDeleteAccountDismissed) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PrivateChatCard(chat: ChatRoom, uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    val other = chat.otherUser ?: return
    PersonCard(
        user = other,
        online = other.uid in uiState.onlineUserIds,
        subtitle = (if (chat.muted) "🔕 " else "") + chat.lastMessagePreview.ifEmpty { "Say hello 👋" },
        subtitleColor = if (chat.unreadCount > 0) colors.textPrimary else null,
        trailingTime = formatListTime(chat.lastMessageAtMillis),
        unread = chat.unreadCount,
        onClick = { viewModel.openChat(chat) },
    )
}

/** The pinned general chat: a bold gradient card with a live "online" pulse. */
@Composable
private fun SpeakyHubCard(hub: ChatRoom?, onlineCount: Int, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val pulse = rememberInfiniteTransition(label = "live")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.sdp))
            .background(Brush.linearGradient(listOf(colors.neonCyan, colors.neonCyanDark, colors.magenta.copy(alpha = 0.85f))))
            .border(1.sdp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.sdp))
            .clickable(onClick = onClick)
            .padding(16.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.sdp)
                    .clip(RoundedCornerShape(14.sdp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.sdp))
            }
            Spacer(Modifier.width(12.sdp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Speaky Hub",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.width(8.sdp))
                    Text(
                        "📌 General",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.sdp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 6.sdp, vertical = 2.sdp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.sdp)) {
                    Box(
                        Modifier
                            .size(8.sdp)
                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                            .background(Color(0xFF7CFFB2), CircleShape)
                    )
                    Spacer(Modifier.width(6.sdp))
                    Text(
                        "$onlineCount online now",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.9f)),
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(22.sdp),
            )
        }
        Spacer(Modifier.height(12.sdp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.sdp))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 12.sdp, vertical = 10.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = hub?.lastMessagePreview?.takeIf { it.isNotEmpty() } ?: "Say hi to learners from around the world 👋",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                modifier = Modifier.weight(1f),
            )
            hub?.lastMessageAtMillis?.takeIf { it > 0 && hub.lastMessagePreview.isNotEmpty() }?.let {
                Text(
                    formatListTime(it),
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 9.ssp),
                    modifier = Modifier.padding(start = 8.sdp),
                )
            }
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.sdp)
            .clip(RoundedCornerShape(16.sdp))
            .background(Brush.verticalGradient(listOf(colors.magenta.copy(alpha = 0.06f), colors.surfaceVariant.copy(alpha = 0.7f))))
            .border(1.sdp, colors.magenta.copy(alpha = 0.22f), RoundedCornerShape(16.sdp))
            .padding(16.sdp),
    ) {
        Text(
            "💌  Private messages",
            style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
        )
        Text(
            "You're reading as a visitor. Sign in with Google to chat in Speaky Hub and message learners privately.",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
            modifier = Modifier.padding(top = 4.sdp, bottom = 12.sdp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.sdp)
                .clip(RoundedCornerShape(22.sdp))
                .background(Color.White)
                .border(1.sdp, colors.textMuted.copy(alpha = 0.2f), RoundedCornerShape(22.sdp))
                .clickable(onClick = onSignIn),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("G  ", fontSize = 16.ssp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
            Text("Sign in with Google", color = Color(0xFF1F1F1F), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PeopleButton(onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier
            .padding(end = 4.sdp)
            .size(40.sdp)
            .clip(RoundedCornerShape(12.sdp))
            .background(colors.gold.copy(alpha = 0.12f))
            .border(1.sdp, colors.gold.copy(alpha = 0.3f), RoundedCornerShape(12.sdp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.People, contentDescription = "People", tint = colors.gold, modifier = Modifier.size(22.sdp))
    }
}

@Composable
private fun AccountMenu(viewModel: CommunityHubViewModel, isAdmin: Boolean) {
    val colors = LocalSpeakMindColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Account", tint = colors.textPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit profile") },
                onClick = {
                    open = false
                    viewModel.onEditProfile()
                },
            )
            if (isAdmin) {
                DropdownMenuItem(
                    text = { Text("🛡️ Reports") },
                    onClick = {
                        open = false
                        viewModel.openReports()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Sign out") },
                onClick = {
                    open = false
                    viewModel.onSignOut()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete account", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    viewModel.onDeleteAccountRequested()
                },
            )
        }
    }
}

@Composable
private fun EditProfileDialog(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    var nickname by remember { mutableStateOf(uiState.myNickname) }
    var gender by remember { mutableStateOf(uiState.myGender.ifEmpty { "male" }) }
    val picker = rememberImagePickerLauncher { bytes -> viewModel.onPhotoPicked(bytes) }
    AlertDialog(
        onDismissRequest = viewModel::onEditProfileDismissed,
        title = { Text("Edit profile") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.clip(CircleShape).clickable { picker.launch() }) {
                    UserAvatar(nickname = nickname.ifEmpty { "?" }, photoUrl = uiState.myPhotoUrl, size = 84.sdp, borderColor = colors.neonCyan)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.sdp)
                            .background(colors.neonCyan, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Change photo", tint = colors.backgroundDark, modifier = Modifier.size(16.sdp))
                    }
                }
                Text(
                    "Tap to change photo",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                    modifier = Modifier.padding(top = 6.sdp, bottom = 12.sdp),
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 30) nickname = it },
                    label = { Text("Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.sdp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.sdp),
                ) {
                    listOf("male" to "👨 Male", "female" to "👩 Female").forEach { (value, label) ->
                        val selected = gender == value
                        Text(
                            label,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (selected) colors.backgroundDark else colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.sdp))
                                .background(if (selected) colors.neonCyan else colors.surfaceVariant)
                                .clickable { gender = value }
                                .padding(vertical = 10.sdp),
                        )
                    }
                }
                if (uiState.isSavingProfile) {
                    LinearProgressIndicator(color = colors.neonCyan, modifier = Modifier.fillMaxWidth().padding(top = 12.sdp))
                }
                uiState.profileError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.sdp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.onSaveProfile(nickname, gender) }, enabled = !uiState.isSavingProfile) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = viewModel::onEditProfileDismissed) { Text("Cancel") } },
    )
}
