package com.speakmind.app.feature.community.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.data.model.ChatRoom
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.model.RoomKind
import com.speakmind.app.feature.community.ui.components.OnlineDot
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.formatLastSeen
import com.speakmind.app.feature.community.ui.components.formatListTime
import com.speakmind.app.navigation.CommunityHubDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.distinctUntilChanged
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
        CommunityHubContent(uiState = uiState, viewModel = viewModel)
    }
}

@Composable
private fun CommunityHubContent(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.sdp, vertical = 10.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                var menuOpen by remember { mutableStateOf(false) }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Community",
                        style = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${uiState.onlineUserIds.size} learners online",
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = colors.textPrimary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (uiState.isGuest) {
                            DropdownMenuItem(
                                text = { Text("Sign in with Google") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onSignInWithGoogle()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                menuOpen = false
                                viewModel.onSignOut()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete account", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                viewModel.onDeleteAccountRequested()
                            },
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = uiState.tab.ordinal,
                containerColor = Color.Transparent,
                contentColor = colors.neonCyan,
            ) {
                HubTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    HubTab.CHATS -> "Chats"
                                    HubTab.GROUPS -> "Groups"
                                    HubTab.PEOPLE -> "People"
                                },
                                color = if (uiState.tab == tab) colors.neonCyan else colors.textMuted,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                }
            }

            if (uiState.isGuest) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.neonCyan.copy(alpha = 0.1f))
                        .clickable { viewModel.onSignInWithGoogle() }
                        .padding(horizontal = 16.sdp, vertical = 10.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "You're a guest: chat in Speaky Hub. Sign in with Google for private chats, groups and voice.",
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                        modifier = Modifier.weight(1f),
                    )
                    Text("Sign in", color = colors.neonCyan, fontWeight = FontWeight.Bold)
                }
            }

            when (uiState.tab) {
                HubTab.CHATS -> ChatsTab(uiState, viewModel)
                HubTab.GROUPS -> GroupsTab(uiState, viewModel)
                HubTab.PEOPLE -> PeopleTab(uiState, viewModel)
            }
        }

        if (uiState.tab == HubTab.GROUPS && !uiState.isGuest) {
            FloatingActionButton(
                onClick = viewModel::onShowCreateGroup,
                containerColor = colors.neonCyan,
                contentColor = colors.backgroundDark,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(18.sdp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create group")
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
    }

    if (uiState.showCreateGroup) {
        CreateGroupDialog(
            isCreating = uiState.isCreatingGroup,
            error = uiState.createGroupError,
            onDismiss = viewModel::onDismissCreateGroup,
            onCreate = viewModel::onCreateGroup,
        )
    }
    if (uiState.showJoinByCode) {
        JoinByCodeDialog(
            error = uiState.joinCodeError,
            onDismiss = viewModel::onDismissJoinByCode,
            onJoin = viewModel::onJoinByCode,
        )
    }
    if (uiState.confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteAccountDismissed,
            title = { Text("Delete your account?") },
            text = {
                Text("Your community profile, messages, voice notes, groups you own (handed to another member) and memberships are permanently deleted. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDeleteAccount) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::onDeleteAccountDismissed) { Text("Cancel") } },
        )
    }
    if (uiState.isDeletingAccount) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.neonCyan)
        }
    }
}

// ---------------- Chats ----------------

@Composable
private fun ChatsTab(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.sdp),
    ) {
        item(key = "hub") {
            RoomRow(
                leading = { IconBadge(Icons.Default.Public) },
                title = "Speaky Hub",
                subtitle = "Global channel · ${uiState.onlineUserIds.size} online",
                time = "",
                unread = 0,
                onClick = viewModel::openSpeakyHub,
            )
        }
        if (uiState.isLoadingRooms && uiState.rooms.isEmpty()) {
            item { CenterSpinner() }
        } else if (uiState.rooms.isEmpty()) {
            item {
                EmptyText("No chats yet.\nFind someone in People or join a group to start chatting!")
            }
        }
        items(uiState.rooms, key = { it.id }) { room ->
            val other = room.otherUser
            RoomRow(
                leading = {
                    if (room.kind == RoomKind.DIRECT && other != null) {
                        AvatarWithStatus(other, online = other.uid in uiState.onlineUserIds)
                    } else {
                        IconBadge(Icons.Default.Groups)
                    }
                },
                title = (if (room.isPrivate) "🔒 " else "") + room.displayName + (if (room.muted) " 🔕" else ""),
                subtitle = room.lastMessagePreview.ifEmpty {
                    if (room.kind == RoomKind.GROUP) "${room.memberCount} members" else "Say hello 👋"
                },
                time = formatListTime(room.lastMessageAtMillis),
                unread = room.unreadCount,
                onClick = { viewModel.openRoom(room) },
            )
        }
    }
}

// ---------------- Groups ----------------

@Composable
private fun GroupsTab(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    Column(Modifier.fillMaxSize()) {
        SearchField(uiState.groupQuery, "Search groups", viewModel::onGroupQueryChanged)
        TextButton(onClick = viewModel::onShowJoinByCode, modifier = Modifier.padding(start = 8.sdp)) {
            Text("🔑  Join a private group with a code", color = LocalSpeakMindColors.current.neonCyan)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.sdp),
        ) {
            if (uiState.isLoadingGroups && uiState.groups.isEmpty()) item { CenterSpinner() }
            else if (uiState.groups.isEmpty()) item { EmptyText("No groups yet. Tap + to create the first one!") }
            items(uiState.groups, key = { it.id }) { group ->
                RoomRow(
                    leading = { IconBadge(Icons.Default.Groups) },
                    title = (if (group.isPrivate) "🔒 " else "") + group.name,
                    subtitle = group.description.ifEmpty { group.lastMessagePreview }
                        .ifEmpty { "${group.memberCount} members" },
                    time = if (group.isMember) "Joined" else "${group.memberCount} 👥",
                    unread = if (group.isMember) group.unreadCount else 0,
                    onClick = { viewModel.openRoom(group) },
                )
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    isCreating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 120) description = it },
                    label = { Text("What's it about? (optional)") },
                    maxLines = 3,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isPrivate = !isPrivate }) {
                    Column(Modifier.weight(1f)) {
                        Text("Private group")
                        Text("Hidden from search; join by invite code only.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, description, isPrivate) }, enabled = !isCreating && name.isNotBlank()) {
                Text(if (isCreating) "Creating…" else "Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun JoinByCodeDialog(error: String?, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join with invite code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isLetterOrDigit() }.take(8) },
                    label = { Text("8-character code") },
                    singleLine = true,
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onJoin(code) }, enabled = code.length == 8) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- People ----------------

@Composable
private fun PeopleTab(uiState: CommunityHubUiState, viewModel: CommunityHubViewModel) {
    val colors = LocalSpeakMindColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 5) viewModel.loadMorePeople() }
    }
    // Online people first, then by most recently seen
    val people = remember(uiState.people, uiState.onlineUserIds) {
        uiState.people.sortedWith(
            compareByDescending<CommunityUser> { it.uid in uiState.onlineUserIds }.thenByDescending { it.lastSeen }
        )
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(uiState.peopleQuery, "Search people", viewModel::onPeopleQueryChanged)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(people, key = { it.uid }) { user ->
                val online = user.uid in uiState.onlineUserIds
                val blocked = user.uid in uiState.blockedUserIds
                RoomRow(
                    leading = { AvatarWithStatus(user, online) },
                    title = user.nickname,
                    subtitle = when {
                        blocked -> "Blocked"
                        online -> "Online"
                        else -> formatLastSeen(user.lastSeen)
                    },
                    subtitleColor = when {
                        blocked -> colors.error
                        online -> colors.success
                        else -> null
                    },
                    time = "",
                    unread = 0,
                    onClick = { viewModel.onPersonClicked(user) },
                )
            }
            if (uiState.isLoadingPeople) item { CenterSpinner() }
            else if (people.isEmpty()) item { EmptyText("No one found.") }
        }
    }
}

// ---------------- Shared rows ----------------

@Composable
private fun RoomRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    time: String,
    unread: Int,
    onClick: () -> Unit,
    subtitleColor: Color? = null,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(color = subtitleColor ?: colors.textMuted),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.sdp)) {
            if (time.isNotEmpty()) {
                Text(time, style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted, fontSize = 9.ssp))
            }
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.sdp, minHeight = 20.sdp)
                        .background(colors.neonCyan, CircleShape)
                        .padding(horizontal = 6.sdp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(color = colors.backgroundDark, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarWithStatus(user: CommunityUser, online: Boolean) {
    val colors = LocalSpeakMindColors.current
    Box {
        UserAvatar(nickname = user.nickname, photoUrl = user.photoUrl, size = 44.sdp, borderColor = colors.neonCyan)
        OnlineDot(online = online, size = 13.sdp, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier
            .size(44.sdp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(colors.neonCyan, colors.neonCyanDark))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.backgroundDark, modifier = Modifier.size(24.sdp))
    }
}

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.sdp, vertical = 10.sdp)
            .clip(RoundedCornerShape(22.sdp))
            .background(colors.surfaceVariant)
            .border(1.sdp, colors.neonCyan.copy(alpha = 0.2f), RoundedCornerShape(22.sdp))
            .padding(horizontal = 14.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.sdp))
        Spacer(Modifier.width(8.sdp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted))
                inner()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CenterSpinner() {
    Box(Modifier.fillMaxWidth().padding(24.sdp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalSpeakMindColors.current.neonCyan, modifier = Modifier.size(28.sdp))
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(color = LocalSpeakMindColors.current.textMuted),
        modifier = Modifier.fillMaxWidth().padding(32.sdp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
