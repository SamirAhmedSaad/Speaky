package com.speakmind.app.feature.story.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.story.domain.model.Story
import com.speakmind.app.feature.story.domain.model.StoryTopic
import com.speakmind.app.navigation.StoriesDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.SpeakMindColors
import com.speakmind.app.ui.theme.levelColorOf
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.storiesScreen() {
    animatedComposable<StoriesDestination> {
        val viewModel = koinViewModel<StoryViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        StoriesScreenContent(
            uiState = uiState,
            onTopicBadgeClick = viewModel::onTopicBadgeClicked,
            onTopicSelected = viewModel::onTopicSelected,
            onTopicPickerDismissed = viewModel::onTopicPickerDismissed,
            onStoryClicked = viewModel::onStoryClicked,
            onRefresh = viewModel::onRefresh,
            onBackClicked = viewModel::onBackClicked,
        )
    }
}

@Composable
private fun StoriesScreenContent(
    uiState: StoriesUiState,
    onTopicBadgeClick: () -> Unit,
    onTopicSelected: (StoryTopic) -> Unit,
    onTopicPickerDismissed: () -> Unit,
    onStoryClicked: (Story) -> Unit,
    onRefresh: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        if (uiState.showTopicPicker) {
            StoryTopicPickerDialog(
                currentTopic = uiState.selectedTopic,
                onTopicSelected = onTopicSelected,
                onDismiss = onTopicPickerDismissed,
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.sdp, bottom = 8.sdp)
                    .padding(horizontal = 16.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "Stories",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                // Topic badge
                val color = topicColor(uiState.selectedTopic)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.sdp))
                        .background(color.copy(alpha = 0.2f))
                        .border(
                            width = 1.sdp,
                            color = color.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.sdp),
                        )
                        .clickable(onClick = onTopicBadgeClick)
                        .padding(horizontal = 12.sdp, vertical = 6.sdp),
                ) {
                    Text(
                        text = uiState.selectedTopic.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = color,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = LocalSpeakMindColors.current.neonCyan)
                    }
                }
                uiState.error != null -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = LocalSpeakMindColors.current.textSecondary,
                                ),
                                modifier = Modifier.padding(horizontal = 32.sdp),
                            )
                        }
                    }
                }
                uiState.stories.isEmpty() -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No stories available for this topic",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = LocalSpeakMindColors.current.textSecondary,
                                ),
                            )
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 12.sdp),
                            verticalArrangement = Arrangement.spacedBy(12.sdp),
                        ) {
                            items(uiState.stories, key = { it.id }) { story ->
                                StoryCard(
                                    story = story,
                                    topic = uiState.selectedTopic,
                                    onClick = { onStoryClicked(story) },
                                )
                            }
                        }
                    }
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StoryTopicPickerDialog(
    currentTopic: StoryTopic,
    onTopicSelected: (StoryTopic) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = {
            Text(
                text = "Choose Story Topic",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                StoryTopic.entries.forEach { topic ->
                    val isSelected = topic == currentTopic
                    val color = topicColor(topic)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.sdp))
                            .background(
                                if (isSelected) color.copy(alpha = 0.15f)
                                else Color.Transparent,
                            )
                            .border(
                                width = if (isSelected) 1.5.sdp else 1.sdp,
                                color = if (isSelected) color.copy(alpha = 0.5f)
                                else colors.surfaceVariant,
                                shape = RoundedCornerShape(12.sdp),
                            )
                            .clickable { onTopicSelected(topic) }
                            .padding(horizontal = 16.sdp, vertical = 12.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.sdp)
                                .clip(RoundedCornerShape(10.sdp))
                                .background(color.copy(alpha = 0.2f)),
                        ) {
                            Text(
                                text = topicEmoji(topic),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.sdp))
                        Column {
                            Text(
                                text = topic.label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = if (isSelected) colors.textPrimary
                                    else colors.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text = "r/${topic.subreddit}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) colors.textSecondary
                                    else colors.textMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun StoryCard(story: Story, topic: StoryTopic, onClick: () -> Unit) {
    val color = topicColor(topic)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(LocalSpeakMindColors.current.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = 1.sdp,
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.sdp),
            )
            .clickable(onClick = onClick)
            .padding(16.sdp),
    ) {
        Column {
            if (story.category.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.sdp))
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 8.sdp, vertical = 4.sdp),
                ) {
                    Text(
                        text = story.category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = color,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(10.sdp))
            }

            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = LocalSpeakMindColors.current.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.sdp))

            Text(
                text = story.content,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = LocalSpeakMindColors.current.textMuted,
                    lineHeight = 18.ssp,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (story.pubDate.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.sdp))
                Text(
                    text = story.pubDate,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = LocalSpeakMindColors.current.textMuted.copy(alpha = 0.7f),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun topicColor(topic: StoryTopic): Color {
    val colors = LocalSpeakMindColors.current
    return when (topic) {
        StoryTopic.HORROR -> colors.magenta
        StoryTopic.FUNNY -> colors.gold
        StoryTopic.SCIFI -> colors.neonCyan
        StoryTopic.WHOLESOME -> colors.success
        StoryTopic.MYSTERY -> levelColorOf("B2")
        StoryTopic.ADVENTURE -> levelColorOf("A2")
        StoryTopic.MOTIVATIONAL -> levelColorOf("A1")
        StoryTopic.LIFE_STORIES -> colors.textSecondary
        StoryTopic.THRILLER -> colors.error
    }
}

private fun topicEmoji(topic: StoryTopic): String = when (topic) {
    StoryTopic.HORROR -> "👻"
    StoryTopic.FUNNY -> "😄"
    StoryTopic.SCIFI -> "🚀"
    StoryTopic.WHOLESOME -> "💛"
    StoryTopic.MYSTERY -> "🔍"
    StoryTopic.ADVENTURE -> "🌍"
    StoryTopic.MOTIVATIONAL -> "💪"
    StoryTopic.LIFE_STORIES -> "📖"
    StoryTopic.THRILLER -> "😱"
}
