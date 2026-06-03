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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.sdp, bottom = 8.sdp)
                    .padding(horizontal = 4.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "My Groups",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onShowCreate) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create group",
                        tint = colors.magenta,
                        modifier = Modifier.size(28.sdp),
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.magenta)
                }
            } else if (uiState.groups.isEmpty()) {
                EmptyGroupsState(onCreateClick = onShowCreate)
            } else {
                Text(
                    text = "Tap a group to manage its words",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(horizontal = 20.sdp, vertical = 4.sdp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 12.sdp),
                    verticalArrangement = Arrangement.spacedBy(12.sdp),
                    modifier = Modifier.weight(1f),
                ) {
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
            title = {
                Text(
                    "Delete group?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.textPrimary, fontWeight = FontWeight.Bold,
                    ),
                )
            },
            text = {
                Text(
                    "\"${group.name}\" and all its words will be deleted.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary),
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(1.sdp, colors.magenta.copy(alpha = 0.25f), RoundedCornerShape(16.sdp))
            .clickable(onClick = onClick)
            .padding(16.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.sdp)
                .clip(RoundedCornerShape(12.sdp))
                .background(colors.magenta.copy(alpha = 0.15f)),
        ) {
            Text(
                text = "${group.wordCount}",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.magenta,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(modifier = Modifier.width(14.sdp))
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium.copy(
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.size(36.sdp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete group",
                tint = colors.textMuted,
                modifier = Modifier.size(18.sdp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.magenta.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun EmptyGroupsState(onCreateClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.sdp),
            modifier = Modifier.padding(horizontal = 40.sdp),
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = colors.magenta.copy(alpha = 0.4f),
                modifier = Modifier.size(64.sdp),
            )
            Text(
                text = "No groups yet",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.textPrimary, fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Create a group to start collecting words by topic, theme, or any category you like.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.textSecondary,
                    lineHeight = 22.ssp,
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.sdp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.sdp))
                    .background(colors.magenta.copy(alpha = 0.15f))
                    .border(1.sdp, colors.magenta.copy(alpha = 0.4f), RoundedCornerShape(12.sdp))
                    .clickable(onClick = onCreateClick)
                    .padding(horizontal = 20.sdp, vertical = 12.sdp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = colors.magenta, modifier = Modifier.size(20.sdp))
                Text(
                    text = "Create First Group",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = colors.magenta, fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
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
                        .border(1.5.sdp, colors.magenta.copy(alpha = 0.4f), RoundedCornerShape(12.sdp))
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
                            cursorBrush = SolidColor(colors.magenta),
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
                Text("Create", color = if (name.isNotBlank()) colors.magenta else colors.textMuted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textMuted)
            }
        },
    )
}
