package com.speakmind.app.feature.vocabgroup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.vocabgroup.domain.model.VocabGroup
import com.speakmind.app.navigation.MyGroupsDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.displayStyle
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.myGroupsScreen() {
    animatedComposable<MyGroupsDestination> {
        val viewModel = koinViewModel<MyGroupsViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        MyGroupsContent(
            uiState = uiState,
            onGroupClick = viewModel::onGroupClicked,
            onDeleteGroup = viewModel::onDeleteGroup,
            onShowCreate = viewModel::onShowCreateDialog,
            onDismissCreate = viewModel::onDismissCreateDialog,
            onCreateGroup = viewModel::onCreateGroup,
            onBack = viewModel::onBack,
        )
    }
}

@Composable
private fun MyGroupsContent(
    uiState: MyGroupsUiState,
    onGroupClick: (VocabGroup) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onShowCreate: () -> Unit,
    onDismissCreate: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    if (uiState.showCreateDialog) {
        CreateGroupDialog(
            onDismiss = onDismissCreate,
            onCreate = onCreateGroup,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeakyBackButton(onClick = onBack)
            Text("My groups", Modifier.weight(1f), style = displayStyle(26.sp), color = colors.textPrimary)
            SpeakyIconButton(
                icon = SpeakyIcons.Plus,
                contentDescription = "Create group",
                onClick = onShowCreate,
                background = colors.neonCyan,
                tint = colors.onPrimary,
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
        } else if (uiState.groups.isEmpty()) {
            EmptyGroupsState(onCreateClick = onShowCreate)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Text("Tap a group to manage its words.", color = colors.textSecondary, fontSize = 14.sp)
                }
                items(uiState.groups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onClick = { onGroupClick(group) },
                        onDelete = { onDeleteGroup(group.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: VocabGroup,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete group?", style = displayStyle(22.sp), color = colors.textPrimary) },
            text = {
                Text(
                    "\"${group.name}\" and all its words will be deleted.",
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colors.textMuted, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(colors.primarySoft),
        ) {
            Text("${group.wordCount}", color = colors.onPrimarySoft, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(group.name, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (group.wordCount == 1) "1 word" else "${group.wordCount} words",
                color = colors.textMuted,
                fontSize = 12.sp,
            )
        }
        SpeakyIconButton(
            icon = SpeakyIcons.Trash,
            contentDescription = "Delete group",
            onClick = { showDeleteConfirm = true },
            size = 40.dp,
            iconSize = 18.dp,
            background = colors.backgroundDark,
            tint = colors.textMuted,
        )
        Icon(SpeakyIcons.Chevron, contentDescription = null, tint = colors.faint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmptyGroupsState(onCreateClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(colors.primarySoft), contentAlignment = Alignment.Center) {
            Icon(SpeakyIcons.Cards, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(40.dp))
        }
        Text("No groups yet", style = displayStyle(24.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            "Create a group to start collecting words by topic, theme, or any category you like.",
            color = colors.textSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        SpeakyPrimaryButton(
            text = "Create first group",
            onClick = onCreateClick,
            leading = SpeakyIcons.Plus,
            height = 54.dp,
            radius = 16.dp,
        )
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                "New Group",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.textPrimary, fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                Text(
                    "Give your group a name",
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.sdp))
                        .background(colors.surfaceVariant.copy(alpha = 0.8f))
                        .border(1.5.sdp, colors.neonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.sdp))
                        .padding(horizontal = 14.sdp, vertical = 12.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (name.isEmpty()) {
                            Text(
                                "e.g. Travel, Business...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
                            )
                        }
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            cursorBrush = SolidColor(colors.neonCyan),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create", color = if (name.isNotBlank()) colors.neonCyan else colors.textMuted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textMuted)
            }
        },
    )
}
