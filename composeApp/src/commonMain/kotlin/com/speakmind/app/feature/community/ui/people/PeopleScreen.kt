package com.speakmind.app.feature.community.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.speakmind.app.config.AppConfig
import com.speakmind.app.feature.community.ui.components.GoogleSignInPill
import com.speakmind.app.feature.community.ui.auth.rememberGoogleSignIn
import com.speakmind.app.navigation.PeopleDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.itemsIndexed
import com.speakmind.app.feature.community.ui.components.CommunityChip
import com.speakmind.app.feature.community.ui.components.CommunityToast
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleContent(uiState: PeopleUiState, viewModel: PeopleViewModel, onSignIn: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 5) viewModel.loadMore() }
    }
    // Online people first, then by most recently seen. The Online tab shows only who is online now:
    // someone who just left disappears at once, before the list reloads.
    val people = remember(uiState.people, uiState.onlineUserIds, uiState.filter) {
        uiState.people
            .filter { uiState.filter != "Online" || it.uid in uiState.onlineUserIds }
            .sortedWith(
                compareByDescending<CommunityUser> { it.uid in uiState.onlineUserIds }.thenByDescending { it.lastSeen },
            )
    }
    val onlineCount = uiState.onlineUserIds.size

    Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
        Column(Modifier.fillMaxSize()) {
            CommunityPageHeader(
                title = "People",
                subtitle = if (onlineCount > 0) "$onlineCount online now" else "Learners in the community",
                subtitleColor = if (onlineCount > 0) colors.success else null,
                onBack = viewModel::onBack,
            )
            CommunitySearchField(
                value = uiState.query,
                placeholder = "Search by name",
                onChange = viewModel::onQueryChanged,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            // One scrolling line, so a narrow phone never leaves a lone chip on a second row.
            Row(
                modifier = Modifier
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PEOPLE_FILTERS.forEach { f ->
                    CommunityChip(label = f, selected = uiState.filter == f, onClick = { viewModel.onFilterSelected(f) })
                }
            }
            if (uiState.isGuest) {
                GoogleSignInPill(
                    "Sign in with Google to message people",
                    loading = uiState.isSigningIn,
                    onClick = onSignIn,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            ) {
                // One white card around all rows (the design's grouped list), built per item.
                itemsIndexed(people, key = { _, user -> user.uid }) { index, user ->
                    val online = user.uid in uiState.onlineUserIds
                    val blocked = user.uid in uiState.blockedUserIds
                    val first = index == 0
                    val last = index == people.lastIndex
                    PersonCard(
                        user = user,
                        online = online,
                        subtitle = listOfNotNull(
                            user.level.takeIf { it.isNotEmpty() },
                            when {
                                blocked -> "Blocked"
                                online -> "Online"
                                else -> "Offline"
                            },
                        ).joinToString(" · "),
                        subtitleColor = if (blocked) colors.error else null,
                        subtitleSize = 13,
                        avatarSize = 52.dp,
                        background = colors.surface,
                        chevron = true,
                        onClick = { viewModel.onPersonClicked(user) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(if (first) 28.dp else 0.dp, if (first) 28.dp else 0.dp, if (last) 28.dp else 0.dp, if (last) 28.dp else 0.dp))
                            .background(colors.surface)
                            .padding(start = 8.dp, end = 8.dp, top = if (first) 8.dp else 1.dp, bottom = if (last) 8.dp else 1.dp),
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
                onMessage = { viewModel.onMessageClicked(user) },
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
            CommunityToast(error, isError = uiState.error != null, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

