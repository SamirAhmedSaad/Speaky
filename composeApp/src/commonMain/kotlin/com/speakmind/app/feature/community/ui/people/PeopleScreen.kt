package com.speakmind.app.feature.community.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
        PeopleContent(uiState, viewModel)
    }
}

@Composable
private fun PeopleContent(uiState: PeopleUiState, viewModel: PeopleViewModel) {
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
            if (uiState.isGuest) {
                Text(
                    "Sign in with Google to message people privately.",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                    modifier = Modifier.padding(horizontal = 24.sdp, vertical = 6.sdp),
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
                        subtitle = when {
                            blocked -> "Blocked"
                            online -> "Online · tap to message"
                            else -> formatLastSeen(user.lastSeen)
                        },
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
                            message = if (uiState.query.isBlank()) "Nobody has joined yet." else "Try a different name.",
                        )
                    }
                }
            }
        }
        uiState.error?.let { error ->
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
                    .background(Color(0xFFB71C1C).copy(alpha = 0.92f))
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            )
        }
    }
}
