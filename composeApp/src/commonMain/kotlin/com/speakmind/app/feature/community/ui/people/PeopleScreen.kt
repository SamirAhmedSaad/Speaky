package com.speakmind.app.feature.community.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.speakmind.app.feature.community.ui.components.ProfileSheet
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.ui.components.CommunityEmptyState
import com.speakmind.app.feature.community.ui.components.CommunityLoading
import com.speakmind.app.feature.community.ui.components.CommunityPageHeader
import com.speakmind.app.feature.community.ui.components.CommunitySearchField
import com.speakmind.app.feature.community.ui.components.PersonCard
import com.speakmind.app.feature.community.ui.components.formatLastSeen
import com.speakmind.app.config.AppConfig
import com.speakmind.app.feature.community.ui.components.GoogleSignInPill
import com.speakmind.app.feature.community.ui.auth.rememberGoogleSignIn
import com.speakmind.app.navigation.PeopleDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.distinctUntilChanged
import network.chaintech.sdpcomposemultiplatform.sdp
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.peopleScreen() {
    animatedComposable<PeopleDestination> {
        val viewModel = koinViewModel<PeopleViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleStartEffect(viewModel) {
            viewModel.onVisibilityChanged(true)
            onStopOrDispose { viewModel.onVisibilityChanged(false) }
        }
        val googlePicker = rememberGoogleSignIn(AppConfig.googleWebClientId)
        PeopleContent(uiState, viewModel, onSignIn = { viewModel.onGoogleSignIn(googlePicker) })
    }
}

@Composable
private fun PeopleContent(uiState: PeopleUiState, viewModel: PeopleViewModel, onSignIn: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 5) viewModel.loadMore() }
    }
    // Online people first, then by most recently seen
    val people = remember(uiState.people, uiState.onlineUserIds) {
        uiState.people.sortedWith(
            compareByDescending<CommunityUser> { it.uid in uiState.onlineUserIds }.thenByDescending { it.lastSeen }
        )
    }
    val onlineCount = people.count { it.uid in uiState.onlineUserIds }

    Box(Modifier.fillMaxSize().background(colors.backgroundGradient)) {
        Column(Modifier.fillMaxSize()) {
            CommunityPageHeader(
                title = "People",
                subtitle = if (onlineCount > 0) "$onlineCount online now" else "Learners in the community",
                onBack = viewModel::onBack,
            )
            CommunitySearchField(
                value = uiState.query,
                placeholder = "Search by name",
                onChange = viewModel::onQueryChanged,
                modifier = Modifier.padding(horizontal = 20.sdp, vertical = 4.sdp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                items(PEOPLE_FILTERS) { f ->
                    val selected = uiState.filter == f
                    Text(
                        if (f == "Online") "● Online" else f,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (selected) colors.backgroundDark else colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.sdp))
                            .background(if (selected) colors.neonCyan else colors.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.sdp, if (selected) colors.neonCyan else colors.neonCyan.copy(alpha = 0.2f), RoundedCornerShape(16.sdp))
                            .clickable { viewModel.onFilterSelected(f) }
                            .padding(horizontal = 14.sdp, vertical = 6.sdp),
                    )
                }
            }
            if (uiState.isGuest) {
                GoogleSignInPill(
                    "Sign in with Google to message people",
                    loading = uiState.isSigningIn,
                    onClick = onSignIn,
                    modifier = Modifier.padding(horizontal = 20.sdp, vertical = 4.sdp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.sdp, end = 20.sdp, top = 10.sdp, bottom = 32.sdp),
                verticalArrangement = Arrangement.spacedBy(10.sdp),
            ) {
                items(people, key = { it.uid }) { user ->
                    val online = user.uid in uiState.onlineUserIds
                    val blocked = user.uid in uiState.blockedUserIds
                    PersonCard(
                        user = user,
                        online = online,
                        subtitle = listOfNotNull(
                            user.level.takeIf { it.isNotEmpty() },
                            when {
                                blocked -> "Blocked"
                                online -> "Online"
                                else -> formatLastSeen(user.lastSeen)
                            },
                        ).joinToString(" · "),
                        subtitleColor = when {
                            blocked -> colors.error
                            online -> colors.success
                            else -> null
                        },
                        onClick = { viewModel.onPersonClicked(user) },
                    )
                }
                if (uiState.isLoading) {
                    item { CommunityLoading() }
                } else if (people.isEmpty()) {
                    item {
                        CommunityEmptyState(
                            emoji = "🔍",
                            title = "No one found",
                            message = when {
                                uiState.filter == "Online" -> "Nobody is online right now."
                                uiState.query.isNotBlank() -> "Try a different name."
                                uiState.filter != "All" -> "No ${uiState.filter} learners yet."
                                else -> "Nobody has joined yet."
                            },
                        )
                    }
                }
            }
        }
        uiState.profileUser?.let { user ->
            ProfileSheet(
                user = user,
                online = user.uid in uiState.onlineUserIds,
                isBlocked = user.uid in uiState.blockedUserIds,
                onDismiss = viewModel::onProfileDismissed,
                onMessage = { viewModel.onMessage(user) },
                onReport = null,
                onBlockToggle = if (uiState.isGuest) null else { { viewModel.onToggleBlock(user) } },
                isModerator = user.uid in uiState.moderatorIds,
                onModeratorToggle = if (uiState.isAdmin) { { viewModel.onToggleModerator(user) } } else null,
            )
        }
        (uiState.error ?: uiState.info)?.let { error ->
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3_500)
                viewModel.onErrorDismissed()
            }
            Text(
                error,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .background(if (uiState.error != null) Color(0xFFB71C1C).copy(alpha = 0.92f) else colors.neonCyanDark)
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            )
        }
    }
}

