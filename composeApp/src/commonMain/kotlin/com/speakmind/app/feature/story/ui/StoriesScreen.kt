package com.speakmind.app.feature.story.ui

import com.speakmind.app.ui.components.rememberInterstitialAdState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.story.domain.model.Story
import com.speakmind.app.feature.story.domain.model.StoryTopic
import com.speakmind.app.navigation.StoriesDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPill
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.storiesScreen() {
    animatedComposable<StoriesDestination> {
        val viewModel = koinViewModel<StoryViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val gate = rememberInterstitialAdState()
        StoriesScreenContent(
            uiState = uiState,
            onTopicBadgeClick = viewModel::onTopicBadgeClicked,
            onTopicSelected = viewModel::onTopicSelected,
            onTopicPickerDismissed = viewModel::onTopicPickerDismissed,
            onStoryClicked = { story -> gate.unlock("story:${story.id}", "Unlock this story") { viewModel.onStoryClicked(story) } },
            onRefresh = viewModel::onRefresh,
            onBackClicked = viewModel::onBackClicked,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    if (uiState.showTopicPicker) {
        StoryTopicPickerDialog(
            currentTopic = uiState.selectedTopic,
            onTopicSelected = onTopicSelected,
            onDismiss = onTopicPickerDismissed,
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
            SpeakyBackButton(onClick = onBackClicked)
            Text("Stories", Modifier.weight(1f), style = displayStyle(26.sp), color = colors.textPrimary)
            val tone = topicTone(uiState.selectedTopic)
            SpeakyPill(
                uiState.selectedTopic.label,
                background = tone.first,
                color = tone.second,
                fontSize = 13.sp,
                onClick = onTopicBadgeClick,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.neonCyan)
                }
                uiState.error != null || uiState.stories.isEmpty() -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            uiState.error ?: "No stories available for this topic",
                            color = colors.textSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Text(
                                "Short reads to enjoy. Tap any word to learn it.",
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                            )
                        }
                        itemsIndexed(uiState.stories, key = { _, it -> it.id }) { index, story ->
                            if (index == 0) {
                                FeaturedStoryCard(story) { onStoryClicked(story) }
                            } else {
                                StoryRow(story, index) { onStoryClicked(story) }
                            }
                        }
                    }
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.CenterHorizontally))
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
        shape = RoundedCornerShape(28.dp),
        title = { Text("Choose a topic", style = displayStyle(22.sp), color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryTopic.entries.forEach { topic ->
                    val isSelected = topic == currentTopic
                    val tone = topicTone(topic)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) colors.primarySoft else colors.backgroundDark)
                            .clickable { onTopicSelected(topic) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tone.first),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(topicEmoji(topic), fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(topic.label, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("r/${topic.subreddit}", color = colors.textMuted, fontSize = 12.sp)
                        }
                        if (isSelected) {
                            Icon(SpeakyIcons.Check, null, tint = colors.neonCyan, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** The newest story, as the big violet card. */
@Composable
private fun FeaturedStoryCard(story: Story, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    // Dark mode's brand tone is a pale lilac; white text needs the deeper violet there.
    // The Premium themes use their own brand colour with its matching text colour.
    val heroColor = when {
        colors.isGold -> colors.neonCyan
        colors.surface.luminance() < 0.5f -> colors.neonCyanDark
        else -> colors.neonCyan
    }
    val onHero = if (colors.isGold) colors.onPrimary else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroColor)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tags = listOfNotNull("NEWEST", story.category.takeIf { it.isNotEmpty() })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach {
                Text(
                    it,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(onHero.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = onHero,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            story.title,
            style = displayStyle(26.sp, lineHeight = 29.sp),
            color = onHero,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            story.content.stripMarkdown(),
            color = if (colors.isGold) onHero.copy(alpha = 0.8f) else Color(0xFFE4DEFF),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(SpeakyIcons.Speaker, null, tint = onHero, modifier = Modifier.size(16.dp))
            Text("Read · listen", color = onHero, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StoryRow(story: Story, index: Int, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val tiles = listOf(
        colors.mintSoft to colors.onMintSoft,
        colors.coralSoft to colors.onCoralSoft,
        colors.primarySoft to colors.onPrimarySoft,
        colors.goldSoft to colors.onGoldSoft,
    )
    val (tileBg, tileFg) = tiles[(index - 1) % tiles.size]
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
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tileBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                story.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                color = tileFg,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                story.title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(story.category, shortDate(story.pubDate)).filter { it.isNotEmpty() }.joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = colors.textMuted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(SpeakyIcons.Chevron, null, tint = colors.faint, modifier = Modifier.size(20.dp))
    }
}

/** "Tue, 03 Nov 2026 10:00:00 +0000" -> "03 Nov 2026". */
private fun shortDate(pubDate: String): String {
    val parts = pubDate.split(" ")
    return if (parts.size >= 4) "${parts[1]} ${parts[2]} ${parts[3]}" else pubDate
}

/** Soft fill and text colour for a topic, from the theme's soft tones. */
@Composable
internal fun topicTone(topic: StoryTopic): Pair<Color, Color> {
    val colors = LocalSpeakMindColors.current
    return when (topic) {
        StoryTopic.HORROR, StoryTopic.THRILLER -> colors.coralSoft to colors.onCoralSoft
        StoryTopic.FUNNY, StoryTopic.LIFE_STORIES -> colors.goldSoft to colors.onGoldSoft
        StoryTopic.WHOLESOME, StoryTopic.MOTIVATIONAL -> colors.mintSoft to colors.onMintSoft
        StoryTopic.SCIFI, StoryTopic.MYSTERY, StoryTopic.ADVENTURE -> colors.primarySoft to colors.onPrimarySoft
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
