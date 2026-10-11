package com.speakmind.app.feature.community.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.community.data.model.GroupMember
import com.speakmind.app.feature.community.data.model.GroupRole
import com.speakmind.app.feature.community.ui.components.OnlineDot
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.formatLastSeen
import com.speakmind.app.navigation.GroupInfoDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.groupInfoScreen() {
    animatedComposable<GroupInfoDestination> { entry ->
        val dest = entry.toRoute<GroupInfoDestination>()
        val viewModel = koinViewModel<GroupInfoViewModel>(key = "info-${dest.roomId}") { parametersOf(dest.roomId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        GroupInfoContent(uiState, viewModel)
    }
}

@Composable
private fun GroupInfoContent(uiState: GroupInfoUiState, vm: GroupInfoViewModel) {
    val colors = LocalSpeakMindColors.current
    val clipboard = LocalClipboardManager.current
    val room = uiState.room

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(3_500)
            vm.onErrorDismissed()
        }
    }
    LaunchedEffect(uiState.info) {
        if (uiState.info != null) {
            kotlinx.coroutines.delay(3_000)
            vm.onInfoDismissed()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.backgroundGradient)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.sdp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.sdp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                    Text(
                        "Group info",
                        style = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.canManage) {
                        IconButton(onClick = vm::onShowEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit group", tint = colors.textPrimary)
                        }
                    }
                }
            }
            if (uiState.isLoading || room == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.sdp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.neonCyan)
                    }
                }
                return@LazyColumn
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 8.sdp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(72.sdp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(colors.neonCyan, colors.neonCyanDark))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = colors.backgroundDark, modifier = Modifier.size(38.sdp))
                    }
                    Spacer(Modifier.height(10.sdp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (room.isPrivate) {
                            Icon(Icons.Default.Lock, contentDescription = "Private", tint = colors.textMuted, modifier = Modifier.size(16.sdp))
                            Spacer(Modifier.width(4.sdp))
                        }
                        Text(
                            room.name,
                            style = MaterialTheme.typography.titleLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                        )
                    }
                    Text(
                        "${if (room.isPrivate) "Private" else "Public"} group · ${uiState.members.size} members",
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
                    )
                    if (room.description.isNotBlank()) {
                        Text(
                            room.description,
                            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(top = 8.sdp),
                        )
                    }
                }
            }
            room.inviteCode?.let { code ->
                item {
                    SectionCard {
                        Text("Invite code", style = MaterialTheme.typography.labelMedium.copy(color = colors.textMuted))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                code.uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = colors.neonCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(code.uppercase()))
                                vm.onCodeCopied()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", tint = colors.textPrimary)
                            }
                            if (uiState.canManage) {
                                IconButton(onClick = vm::onRegenerateCode) {
                                    Icon(Icons.Default.Refresh, contentDescription = "New code", tint = colors.textPrimary)
                                }
                            }
                        }
                        Text(
                            "Friends can join with this code from Groups → Join with code.",
                            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                        )
                    }
                }
            }
            item {
                SectionCard {
                    ActionRow(
                        if (room.muted) "Unmute notifications" else "Mute notifications",
                        if (room.muted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        onClick = vm::onToggleMute,
                    )
                    ActionRow("Leave group", Icons.AutoMirrored.Filled.ExitToApp, destructive = true, onClick = vm::onLeaveRequested)
                    if (uiState.isOwner) {
                        ActionRow("Delete group", Icons.Default.Delete, destructive = true, onClick = vm::onDeleteRequested)
                    }
                }
            }
            item {
                Text(
                    "Members",
                    style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 20.sdp, vertical = 8.sdp),
                )
            }
            items(uiState.members, key = { it.user.uid }) { member ->
                MemberRow(
                    member = member,
                    isMe = member.user.uid == uiState.currentUserId,
                    online = member.user.uid in uiState.onlineUserIds,
                    myRole = uiState.myRole,
                    onMessage = { vm.onMessageMember(member.user) },
                    onRemove = { vm.onRemoveMember(member) },
                    onSetRole = { vm.onSetRole(member, it) },
                )
            }
        }

        (uiState.error ?: uiState.info)?.let { msg ->
            Text(
                msg,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .background(if (uiState.error != null) Color(0xFFB71C1C) else colors.neonCyanDark)
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            )
        }
    }

    if (uiState.showEdit && room != null) {
        EditGroupDialog(
            initialName = room.name,
            initialDescription = room.description,
            initialPrivate = room.isPrivate,
            onDismiss = vm::onDismissEdit,
            onSave = vm::onSaveEdit,
        )
    }
    if (uiState.confirmLeave) {
        ConfirmDialog(
            title = "Leave this group?",
            text = if (uiState.isOwner) "Ownership passes to an admin or the longest-standing member." else "You can join again later if the group is public.",
            confirm = "Leave",
            onConfirm = vm::onConfirmLeave,
            onDismiss = vm::onConfirmDismissed,
        )
    }
    if (uiState.confirmDelete) {
        ConfirmDialog(
            title = "Delete this group?",
            text = "All messages are removed for everyone. This can't be undone.",
            confirm = "Delete",
            onConfirm = vm::onConfirmDelete,
            onDismiss = vm::onConfirmDismissed,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.sdp, vertical = 6.sdp)
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surface.copy(alpha = 0.8f))
            .padding(14.sdp),
        content = content,
    )
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val tint = if (destructive) colors.error else colors.textPrimary
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.sdp))
        Spacer(Modifier.width(12.sdp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    isMe: Boolean,
    online: Boolean,
    myRole: GroupRole?,
    onMessage: () -> Unit,
    onRemove: () -> Unit,
    onSetRole: (GroupRole) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val canRemove = !isMe && member.role != GroupRole.OWNER &&
        (myRole == GroupRole.OWNER || (myRole == GroupRole.ADMIN && member.role == GroupRole.MEMBER))
    val canPromote = !isMe && myRole == GroupRole.OWNER && member.role != GroupRole.OWNER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isMe) { onMessage() }
            .padding(horizontal = 20.sdp, vertical = 8.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            UserAvatar(nickname = member.user.nickname, photoUrl = member.user.photoUrl, size = 40.sdp, borderColor = colors.neonCyan)
            OnlineDot(online = online, size = 12.sdp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(
                member.user.nickname + if (isMe) " (you)" else "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
            )
            Text(
                if (online) "Online" else formatLastSeen(member.user.lastSeen),
                style = MaterialTheme.typography.bodySmall.copy(color = if (online) colors.success else colors.textMuted),
            )
        }
        if (member.role != GroupRole.MEMBER) {
            Text(
                if (member.role == GroupRole.OWNER) "Owner" else "Admin",
                style = MaterialTheme.typography.labelSmall.copy(color = colors.neonCyan, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.sdp))
                    .background(colors.neonCyan.copy(alpha = 0.12f))
                    .padding(horizontal = 8.sdp, vertical = 3.sdp),
            )
        }
        if (canRemove || canPromote) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Member options", tint = colors.textMuted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canPromote) {
                        val makeAdmin = member.role == GroupRole.MEMBER
                        DropdownMenuItem(
                            text = { Text(if (makeAdmin) "Make admin" else "Remove admin") },
                            onClick = {
                                menuOpen = false
                                onSetRole(if (makeAdmin) GroupRole.ADMIN else GroupRole.MEMBER)
                            },
                        )
                    }
                    if (canRemove) {
                        DropdownMenuItem(
                            text = { Text("Remove from group", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditGroupDialog(
    initialName: String,
    initialDescription: String,
    initialPrivate: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var isPrivate by remember { mutableStateOf(initialPrivate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit group") },
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
                    label = { Text("Description") },
                    maxLines = 3,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isPrivate = !isPrivate }) {
                    Column(Modifier.weight(1f)) {
                        Text("Private group")
                        Text(
                            "Hidden from search; join by invite code only.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, description, isPrivate) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
