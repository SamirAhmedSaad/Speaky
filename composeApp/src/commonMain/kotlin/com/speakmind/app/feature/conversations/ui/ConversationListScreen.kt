package com.speakmind.app.feature.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.core.domain.model.Conversation
import com.speakmind.app.navigation.ConversationListDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.SpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.conversationListScreen() {
    animatedComposable<ConversationListDestination> {
        val viewModel = koinViewModel<ConversationListViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        ConversationListContent(
            uiState = uiState,
            onNewChat = viewModel::onNewChat,
            onOpenConversation = viewModel::onOpenConversation,
            onDeleteConversation = viewModel::onDeleteConversation,
            onNavigateBack = viewModel::onNavigateBack,
            onRefresh = viewModel::loadConversations,
        )
    }
}

@Composable
private fun ConversationListContent(
    uiState: ConversationListUiState,
    onNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        containerColor = SpeakMindColors.backgroundDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpeakMindColors.textPrimary,
                    )
                }
                Text(
                    text = "Chat History",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SpeakMindColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = SpeakMindColors.neonCyan,
                contentColor = SpeakMindColors.backgroundDark,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = SpeakMindColors.neonCyan,
                    )
                }
                uiState.conversations.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = SpeakMindColors.textMuted,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No conversations yet",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = SpeakMindColors.textSecondary,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Start a new chat to begin learning!",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SpeakMindColors.textMuted,
                            ),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.conversations, key = { it.id }) { conversation ->
                            ConversationItem(
                                conversation = conversation,
                                onClick = { onOpenConversation(conversation.id) },
                                onDelete = { onDeleteConversation(conversation.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SpeakMindColors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chat icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SpeakMindColors.neonCyanDark,
                                SpeakMindColors.neonCyan,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = SpeakMindColors.backgroundDark,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = SpeakMindColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (conversation.lastMessagePreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = conversation.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SpeakMindColors.textMuted,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${conversation.messageCount} messages",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SpeakMindColors.textMuted,
                    ),
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = SpeakMindColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
