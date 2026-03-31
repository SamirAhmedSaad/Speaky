package com.speakmind.app.feature.home.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.home.domain.model.DailyCard
import com.speakmind.app.feature.home.domain.model.getLevelColor
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.SpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.homeScreen() {
    animatedComposable<HomeDestination> {
        val viewModel = koinViewModel<HomeViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        HomeScreenContent(
            uiState = uiState,
            onScenarioClick = viewModel::onScenarioClicked,
            onFreeTalkClick = viewModel::onFreeTalkClicked,
            onFlashcardsClick = viewModel::onFlashcardsClicked,
            onLevelBadgeClick = viewModel::onLevelBadgeClicked,
            onLevelSelected = viewModel::onLevelSelected,
            onLevelPickerDismissed = viewModel::onLevelPickerDismissed,
        )
    }
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onScenarioClick: (String) -> Unit,
    onFreeTalkClick: () -> Unit,
    onFlashcardsClick: () -> Unit,
    onLevelBadgeClick: () -> Unit,
    onLevelSelected: (String) -> Unit,
    onLevelPickerDismissed: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeakMindColors.backgroundGradient)
    ) {
        // Level Picker Dialog
        if (uiState.showLevelPicker) {
            LevelPickerDialog(
                currentLevel = uiState.userLevel,
                onLevelSelected = onLevelSelected,
                onDismiss = onLevelPickerDismissed,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Header with greeting and streak
            item {
                HomeHeader(
                    streakDays = uiState.streakDays,
                    userLevel = uiState.userLevel,
                    onLevelClick = onLevelBadgeClick,
                )
            }

            // Stats row
            item {
                StatsRow(
                    totalVocab = uiState.totalVocab,
                    totalConversations = uiState.totalConversations,
                    totalMinutes = uiState.totalMinutes,
                )
            }

            // Daily Topics section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Daily Topics",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SpeakMindColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Horizontal scrollable scenario cards
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.dailyCards) { card ->
                        ScenarioCard(
                            card = card,
                            onClick = { onScenarioClick(card.scenario.id) }
                        )
                    }
                }
            }

            // Flashcard review button
            item {
                Spacer(modifier = Modifier.height(24.dp))
                FlashcardReviewButton(
                    dueCount = uiState.flashcardDueCount,
                    onClick = onFlashcardsClick
                )
            }
        }

        // Free Talk FAB
        FreeTalkButton(
            onClick = onFreeTalkClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun HomeHeader(streakDays: Int, userLevel: String, onLevelClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 60.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Hello! \uD83D\uDC4B",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = SpeakMindColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "What do you want to talk about today?",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = SpeakMindColors.textSecondary
                    )
                )
            }

            // Level badge (tappable to change)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(getLevelColor(userLevel)).copy(alpha = 0.2f))
                    .border(
                        width = 1.dp,
                        color = Color(getLevelColor(userLevel)).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onLevelClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = userLevel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color(getLevelColor(userLevel)),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Streak badge
        if (streakDays > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SpeakMindColors.gold.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "\uD83D\uDD25",
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$streakDays day streak",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = SpeakMindColors.gold,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun StatsRow(totalVocab: Long, totalConversations: Long, totalMinutes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatChip(
            value = "$totalVocab",
            label = "Words",
            icon = Icons.Default.School,
            color = SpeakMindColors.neonCyan,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            value = "$totalConversations",
            label = "Chats",
            icon = Icons.Default.ChatBubble,
            color = SpeakMindColors.magenta,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            value = "$totalMinutes",
            label = "Min",
            icon = Icons.Default.Bolt,
            color = SpeakMindColors.gold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SpeakMindColors.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                color = SpeakMindColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = SpeakMindColors.textMuted
            )
        )
    }
}

@Composable
private fun ScenarioCard(card: DailyCard, onClick: () -> Unit) {
    val scenario = card.scenario
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpeakMindColors.surfaceVariant.copy(alpha = 0.8f),
                        SpeakMindColors.surface.copy(alpha = 0.6f),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = SpeakMindColors.neonCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.categoryIcon,
                    fontSize = 28.sp
                )
                // Level badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(getLevelColor(scenario.level)).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = scenario.level,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(getLevelColor(scenario.level)),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = scenario.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = SpeakMindColors.textPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = scenario.category,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = SpeakMindColors.neonCyan.copy(alpha = 0.7f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = scenario.emotionalStakes,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = SpeakMindColors.textMuted
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FlashcardReviewButton(dueCount: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SpeakMindColors.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = 1.dp,
                color = SpeakMindColors.magenta.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Style,
            contentDescription = null,
            tint = SpeakMindColors.magenta,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Review Flashcards",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = SpeakMindColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = if (dueCount > 0) "$dueCount cards due today" else "No cards due",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = SpeakMindColors.textSecondary
                )
            )
        }
        if (dueCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SpeakMindColors.magenta)
            ) {
                Text(
                    text = "$dueCount",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun FreeTalkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SpeakMindColors.neonCyan.copy(alpha = glowAlpha * 0.3f))
        )
        // Button
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = SpeakMindColors.neonCyan,
                contentColor = SpeakMindColors.backgroundDark
            ),
            modifier = Modifier.size(64.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = "Free Talk",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun LevelPickerDialog(
    currentLevel: String,
    onLevelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpeakMindColors.surface,
        titleContentColor = SpeakMindColors.textPrimary,
        title = {
            Text(
                text = "Choose Your Level",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_LEVELS.forEach { level ->
                    val isSelected = level == currentLevel
                    val levelColor = Color(getLevelColor(level))
                    val description = when (level) {
                        "A1" -> "Beginner — basic words and phrases"
                        "A2" -> "Elementary — simple conversations"
                        "B1" -> "Intermediate — everyday topics"
                        "B2" -> "Upper Intermediate — complex discussions"
                        "C1" -> "Advanced — fluent and natural"
                        else -> ""
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) levelColor.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) levelColor.copy(alpha = 0.5f)
                                else SpeakMindColors.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onLevelSelected(level) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(levelColor.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = level,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = levelColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isSelected) SpeakMindColors.textPrimary
                                else SpeakMindColors.textSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}
