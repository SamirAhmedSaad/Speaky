package com.speakmind.app.feature.community.ui.users

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.navigation.CommunityUsersListDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.communityUsersScreen() {
    animatedComposable<CommunityUsersListDestination> {
        val viewModel = koinViewModel<CommunityUsersViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val navigationManager = koinInject<NavigationManager>()
        CommunityUsersContent(
            uiState = uiState,
            onSearchChanged = viewModel::onSearchQueryChanged,
            onUserClick = viewModel::onUserClicked,
            onBack = { navigationManager.back() },
        )
    }
}

@Composable
private fun CommunityUsersContent(
    uiState: CommunityUsersUiState,
    onSearchChanged: (String) -> Unit,
    onUserClick: (CommunityUser) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.sdp, vertical = 12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(4.sdp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Community",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "Find learners to practice with",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.textMuted,
                    ),
                )
            }
            // Online indicator dot
            Box(
                modifier = Modifier
                    .size(10.sdp)
                    .background(colors.success, CircleShape)
            )
        }

        // Search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.sdp, vertical = 4.sdp)
                .clip(RoundedCornerShape(14.sdp))
                .background(colors.surfaceVariant.copy(alpha = 0.6f))
                .border(
                    width = 1.sdp,
                    color = colors.neonCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.sdp),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.sdp, vertical = 12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.sdp),
                )
                Spacer(modifier = Modifier.width(8.sdp))
                BasicTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textPrimary,
                    ),
                    decorationBox = { inner ->
                        if (uiState.searchQuery.isEmpty()) {
                            Text(
                                "Search by nickname...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.textMuted,
                                ),
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.sdp))

        // Users list
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
            }
            uiState.users.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No learners found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.textSecondary,
                            ),
                        )
                        if (uiState.searchQuery.isNotEmpty()) {
                            Text(
                                text = "Try a different nickname",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colors.textMuted,
                                ),
                            )
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = 16.sdp,
                        vertical = 8.sdp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.sdp),
                ) {
                    items(uiState.users, key = { it.uid }) { user ->
                        UserCard(
                            user = user,
                            unreadCount = uiState.unreadCounts[user.uid] ?: 0,
                            onClick = { onUserClick(user) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: CommunityUser,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val genderColor = if (user.gender == "female") colors.magenta else colors.neonCyan
    val genderEmoji = if (user.gender == "female") "👩" else "👨"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        genderColor.copy(alpha = 0.09f),
                        colors.surfaceVariant.copy(alpha = 0.7f),
                        colors.surface.copy(alpha = 0.45f),
                    )
                )
            )
            .border(
                width = 1.sdp,
                color = genderColor.copy(alpha = 0.22f),
                shape = RoundedCornerShape(16.sdp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.sdp, vertical = 12.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.sdp),
    ) {
        // Gender emoji circle
        Box(
            modifier = Modifier
                .size(40.sdp)
                .background(genderColor.copy(alpha = 0.12f), CircleShape)
                .border(1.sdp, genderColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = genderEmoji, fontSize = 18.ssp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.sdp),
            ) {
                Text(
                    text = user.nickname,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(2.sdp))
            Text(
                text = formatLastSeen(user.lastSeen),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(22.sdp)
                    .background(colors.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.ssp,
                    ),
                )
            }
        }
    }
}

private fun formatLastSeen(seconds: Long): String {
    if (seconds == 0L) return "Offline"
    val nowSeconds = System.currentTimeMillis() / 1000L
    val diff = nowSeconds - seconds
    return when {
        diff < 60 -> "Online now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}

@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle,
        decorationBox = decorationBox,
        modifier = modifier,
    )
}
