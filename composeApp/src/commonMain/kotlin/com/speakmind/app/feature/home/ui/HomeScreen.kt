package com.speakmind.app.feature.home.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.dailyword.domain.model.DailyWordData
import com.speakmind.app.feature.home.domain.model.DailyCard
import com.speakmind.app.ui.theme.levelColorOf
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.homeScreen() {
    animatedComposable<HomeDestination> {
        val viewModel = koinViewModel<HomeViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshFlashcardCount()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val context = LocalContext.current

        val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            viewModel.onResumeFromExactAlarmSettings()
        }

        // POST_NOTIFICATIONS launcher — fires after the system dialog result
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) viewModel.onNotificationToggled(true)
        }

        fun handleNotificationToggle(enabled: Boolean) {
            if (!enabled) {
                viewModel.onNotificationToggled(false)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    viewModel.onNotificationToggled(true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.onNotificationToggled(true)
            }
        }

        HomeScreenContent(
            uiState = uiState,
            onScenarioClick = viewModel::onScenarioClicked,
            onCloudChatClick = viewModel::onCloudChatClicked,
            onFlashcardsClick = viewModel::onFlashcardsClicked,
            onVocabularyClick = viewModel::onVocabularyClicked,
            onStoriesClick = viewModel::onStoriesClicked,
            onLevelBadgeClick = viewModel::onLevelBadgeClicked,
            onLevelSelected = viewModel::onLevelSelected,
            onLevelPickerDismissed = viewModel::onLevelPickerDismissed,
            onThemeToggle = viewModel::onThemeToggle,
            onDailyWordClick = viewModel::onDailyWordClicked,
            onTimePickerClick = viewModel::onTimePickerClicked,
            onTimePickerDismissed = viewModel::onTimePickerDismissed,
            onNotificationTimeChanged = viewModel::onNotificationTimeChanged,
            onNotificationToggled = ::handleNotificationToggle,
            onWordLookupClick = viewModel::onWordLookupClicked,
            onAllLearnedWordsClick = viewModel::onAllLearnedWordsClicked,
            onExactAlarmRationaleDismissed = viewModel::onExactAlarmRationaleDismissed,
            onExactAlarmRationaleConfirmed = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    exactAlarmSettingsLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    )
                }
                viewModel.onExactAlarmRationaleDismissed()
            },
        )
    }
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onScenarioClick: (String) -> Unit,
    onCloudChatClick: () -> Unit,
    onFlashcardsClick: () -> Unit,
    onVocabularyClick: () -> Unit,
    onStoriesClick: () -> Unit,
    onLevelBadgeClick: () -> Unit,
    onLevelSelected: (String) -> Unit,
    onLevelPickerDismissed: () -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onDailyWordClick: (Long) -> Unit,
    onTimePickerClick: () -> Unit,
    onTimePickerDismissed: () -> Unit,
    onNotificationTimeChanged: (Int, Int) -> Unit,
    onNotificationToggled: (Boolean) -> Unit,
    onWordLookupClick: () -> Unit,
    onAllLearnedWordsClick: () -> Unit,
    onExactAlarmRationaleDismissed: () -> Unit = {},
    onExactAlarmRationaleConfirmed: () -> Unit = {},
) {
    val colors = LocalSpeakMindColors.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        // Level Picker Dialog
        if (uiState.showLevelPicker) {
            LevelPickerDialog(
                currentLevel = uiState.userLevel,
                onLevelSelected = onLevelSelected,
                onDismiss = onLevelPickerDismissed,
            )
        }

        // Time Picker Dialog
        if (uiState.showTimePicker) {
            TimePickerDialog(
                currentHour = uiState.notificationHour,
                currentMinute = uiState.notificationMinute,
                onTimeSelected = onNotificationTimeChanged,
                onDismiss = onTimePickerDismissed,
            )
        }

        // Exact Alarm Permission Rationale Dialog
        if (uiState.showExactAlarmRationale) {
            AlertDialog(
                onDismissRequest = onExactAlarmRationaleDismissed,
                title = {
                    Text(
                        "Deliver notification on time",
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text("To send your daily word at exactly the time you chose, allow SpeakMind to schedule precise notifications. Without this, it may arrive up to 15 minutes late.")
                },
                confirmButton = {
                    TextButton(onClick = onExactAlarmRationaleConfirmed) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onExactAlarmRationaleDismissed) {
                        Text("Not now")
                    }
                },
            )
        }

        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.sdp)
            ) {
            // Header with greeting
            item {
                HomeHeader(
                    streakDays = uiState.streakDays,
                    userLevel = uiState.userLevel,
                    userName = uiState.userName,
                    onLevelClick = onLevelBadgeClick,
                    isDark = isDark,
                    onThemeToggle = onThemeToggle,
                )
            }

            // Daily Words section (first)
            item {
                Spacer(modifier = Modifier.height(20.sdp))
                DailyWordsSection(
                    todayWord = uiState.todayWord,
                    recentWords = uiState.recentDailyWords,
                    notificationsEnabled = uiState.notificationsEnabled,
                    notificationHour = uiState.notificationHour,
                    notificationMinute = uiState.notificationMinute,
                    onWordClick = onDailyWordClick,
                    onBellClick = onTimePickerClick,
                    onToggleNotifications = onNotificationToggled,
                    onAllLearnedWordsClick = onAllLearnedWordsClick,
                )
            }

            // Study Hub section
            item {
                Spacer(modifier = Modifier.height(24.sdp))
                Text(
                    text = "Study Hub",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 20.sdp)
                )
                Spacer(modifier = Modifier.height(12.sdp))
                WordLookupBox(
                    onClick = onWordLookupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.sdp),
                )
                Spacer(modifier = Modifier.height(12.sdp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.sdp),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp),
                ) {
                    WordBuilderBox(
                        onClick = onVocabularyClick,
                        modifier = Modifier.weight(1f),
                    )
                    ReviewFlashcardsBox(
                        dueCount = uiState.flashcardDueCount,
                        onClick = onFlashcardsClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Reading & Listening section
            item {
                Spacer(modifier = Modifier.height(24.sdp))
                ReadStoriesButton(onClick = onStoriesClick)
            }

            // Speaky Chat
            item {
                Spacer(modifier = Modifier.height(24.sdp))
                CloudChatButton(onClick = onCloudChatClick)
            }

            // Daily Topics section
            item {
                Spacer(modifier = Modifier.height(24.sdp))
                Text(
                    text = "Daily Topics",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 20.sdp)
                )
                Spacer(modifier = Modifier.height(12.sdp))
            }

            // Horizontal scrollable scenario cards or shimmer loading
            item {
                if (uiState.isLoadingTopics) {
                    ShimmerTopicsRow()
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.sdp),
                        horizontalArrangement = Arrangement.spacedBy(14.sdp)
                    ) {
                        items(uiState.dailyCards) { card ->
                            ScenarioCard(
                                card = card,
                                onClick = { onScenarioClick(card.scenario.id) }
                            )
                        }
                    }
                }
            }
        }

        BannerAdView(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HomeHeader(
    streakDays: Int,
    userLevel: String,
    userName: String,
    onLevelClick: () -> Unit,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.sdp)
            .padding(top = 60.sdp, bottom = 8.sdp)
    ) {
        // Top bar: Hello + Name | Streak | Level | Theme toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Greeting
            Column(
                modifier = Modifier.weight(1f,false)
            ) {
                Text(
                    text = "Hello",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary
                    )
                )
                Text(
                    text = if (userName.isNotEmpty()) "$userName \uD83D\uDC4B" else "there \uD83D\uDC4B",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expandable streak badge
                if (streakDays > 0) {
                    var streakExpanded by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(40.sdp)
                            .clip(RoundedCornerShape(12.sdp))
                            .background(colors.gold.copy(alpha = 0.15f))
                            .border(
                                width = 1.sdp,
                                color = colors.gold.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.sdp)
                            )
                            .clickable { streakExpanded = !streakExpanded }
                            .animateContentSize(animationSpec = tween(300))
                            .padding(horizontal = if (streakExpanded) 12.sdp else 10.sdp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "\uD83D\uDD25", fontSize = 16.ssp)
                        Spacer(modifier = Modifier.width(4.sdp))
                        Text(
                            text = if (streakExpanded) "$streakDays day streak" else "$streakDays",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = colors.gold,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Level badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.sdp)
                        .clip(RoundedCornerShape(12.sdp))
                        .background(levelColorOf(userLevel).copy(alpha = 0.2f))
                        .border(
                            width = 1.sdp,
                            color = levelColorOf(userLevel).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.sdp)
                        )
                        .clickable(onClick = onLevelClick)
                ) {
                    Text(
                        text = userLevel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = levelColorOf(userLevel),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Theme toggle
                Box(
                    modifier = Modifier
                        .size(40.sdp)
                        .clip(RoundedCornerShape(12.sdp))
                        .clickable{ onThemeToggle(isDark) }
                        .background(colors.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                        tint = colors.gold,
                        modifier = Modifier.size(22.sdp)
                    )
                }
            }
        }
    }
}



@Composable
private fun ScenarioCard(card: DailyCard, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val scenario = card.scenario
    Box(
        modifier = Modifier
            .width(200.sdp)
            .height(200.sdp)
            .clip(RoundedCornerShape(20.sdp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.surfaceVariant.copy(alpha = 0.8f),
                        colors.surface.copy(alpha = 0.6f),
                    )
                )
            )
            .border(
                width = 1.sdp,
                color = colors.neonCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.sdp)
            )
            .clickable(onClick = onClick)
            .padding(16.sdp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.categoryIcon,
                    fontSize = 28.ssp
                )
                // Level badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.sdp))
                        .background(levelColorOf(scenario.level).copy(alpha = 0.2f))
                        .padding(horizontal = 8.sdp, vertical = 2.sdp)
                ) {
                    Text(
                        text = scenario.level,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = levelColorOf(scenario.level),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.sdp))

            Text(
                text = scenario.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.sdp))

            Text(
                text = scenario.category,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.neonCyan.copy(alpha = 0.7f)
                )
            )

            Spacer(modifier = Modifier.height(8.sdp))

            Text(
                text = scenario.emotionalStakes,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textMuted
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WordBuilderBox(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.sdp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.neonCyan.copy(alpha = 0.08f),
                        colors.surfaceVariant.copy(alpha = 0.7f),
                    )
                )
            )
            .border(
                width = 1.sdp,
                color = colors.neonCyan.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.sdp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.sdp, horizontal = 12.sdp),
    ) {
        Icon(
            imageVector = Icons.Default.Translate,
            contentDescription = null,
            tint = colors.neonCyan,
            modifier = Modifier.size(32.sdp)
        )
        Spacer(modifier = Modifier.height(10.sdp))
        Text(
            text = "Word Builder",
            style = MaterialTheme.typography.titleSmall.copy(
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        )
        Spacer(modifier = Modifier.height(2.sdp))
        Text(
            text = "Build vocabulary",
            style = MaterialTheme.typography.labelSmall.copy(
                color = colors.textMuted,
            )
        )
    }
}

@Composable
private fun ReviewFlashcardsBox(dueCount: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.sdp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.magenta.copy(alpha = 0.08f),
                        colors.surfaceVariant.copy(alpha = 0.7f),
                    )
                )
            )
            .border(
                width = 1.sdp,
                color = colors.magenta.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.sdp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.sdp, horizontal = 12.sdp),
    ) {
        Box {
            Icon(
                imageVector = Icons.Default.Style,
                contentDescription = null,
                tint = colors.magenta,
                modifier = Modifier.size(32.sdp)
            )
            if (dueCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.sdp, y = (-4).sdp)
                        .size(18.sdp)
                        .clip(CircleShape)
                        .background(colors.magenta)
                ) {
                    Text(
                        text = "$dueCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.ssp,
                        )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.sdp))
        Text(
            text = "Review",
            style = MaterialTheme.typography.titleSmall.copy(
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        )
        Spacer(modifier = Modifier.height(2.sdp))
        Text(
            text = if (dueCount > 0) "$dueCount cards due" else "No cards due",
            style = MaterialTheme.typography.labelSmall.copy(
                color = colors.textMuted,
            )
        )
    }
}

@Composable
private fun WordLookupBox(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.sdp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.neonCyan.copy(alpha = 0.05f),
                        colors.surfaceVariant.copy(alpha = 0.7f),
                    )
                )
            )
            .border(
                width = 1.sdp,
                color = colors.neonCyan.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.sdp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp, vertical = 14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.sdp)
                .clip(RoundedCornerShape(12.sdp))
                .background(colors.neonCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.neonCyan,
                modifier = Modifier.size(24.sdp),
            )
        }
        Spacer(modifier = Modifier.width(12.sdp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Word Lookup",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            )
            Text(
                text = "Type any word — get meaning & examples",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = colors.textMuted,
                )
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.neonCyan.copy(alpha = 0.5f),
            modifier = Modifier.size(20.sdp),
        )
    }
}

@Composable
private fun ReadStoriesButton(onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.sdp)
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = 1.sdp,
                color = colors.gold.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.sdp)
            )
            .clickable(onClick = onClick)
            .padding(16.sdp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(32.sdp)
        )
        Spacer(modifier = Modifier.width(12.sdp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Read Stories",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "read stories by levels",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary
                )
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(24.sdp)
        )
    }
}

@Composable
private fun CloudChatButton(onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.sdp)
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = 1.sdp,
                color = Color(0xFF7C4DFF).copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.sdp)
            )
            .clickable(onClick = onClick)
            .padding(16.sdp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFF7C4DFF),
            modifier = Modifier.size(32.sdp)
        )
        Spacer(modifier = Modifier.width(12.sdp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Speaky Chat",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                )
                Spacer(modifier = Modifier.width(8.sdp))
                Text(
                    text = "BETA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF7C4DFF),
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.sdp))
                        .background(Color(0xFF7C4DFF).copy(alpha = 0.15f))
                        .padding(horizontal = 6.sdp, vertical = 2.sdp)
                )
            }
            Text(
                text = "Chat with Sage · No sign-in needed",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary,
                )
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF7C4DFF),
            modifier = Modifier.size(24.sdp)
        )
    }
}

@Composable
private fun LevelPickerDialog(
    currentLevel: String,
    onLevelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = {
            Text(
                text = "Choose Your Level",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                ALL_LEVELS.forEach { level ->
                    val isSelected = level == currentLevel
                    val levelColor = levelColorOf(level)
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
                            .clip(RoundedCornerShape(12.sdp))
                            .background(
                                if (isSelected) levelColor.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 1.5.sdp else 1.sdp,
                                color = if (isSelected) levelColor.copy(alpha = 0.5f)
                                else colors.surfaceVariant,
                                shape = RoundedCornerShape(12.sdp)
                            )
                            .clickable { onLevelSelected(level) }
                            .padding(horizontal = 16.sdp, vertical = 12.sdp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.sdp)
                                .clip(RoundedCornerShape(10.sdp))
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
                        Spacer(modifier = Modifier.width(12.sdp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isSelected) colors.textPrimary
                                else colors.textSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DailyWordsSection(
    todayWord: DailyWordData?,
    recentWords: List<DailyWordData>,
    notificationsEnabled: Boolean,
    notificationHour: Int,
    notificationMinute: Int,
    onWordClick: (Long) -> Unit,
    onBellClick: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onAllLearnedWordsClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    Column(modifier = Modifier.padding(horizontal = 20.sdp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Daily Words",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onBellClick),
            ) {
                if (notificationsEnabled) {
                    val amPm = if (notificationHour < 12) "AM" else "PM"
                    val displayHour = when {
                        notificationHour == 0 -> 12
                        notificationHour > 12 -> notificationHour - 12
                        else -> notificationHour
                    }
                    Text(
                        text = "%d:%02d %s".format(displayHour, notificationMinute, amPm),
                        style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                    )
                }
                Icon(
                    imageVector = if (notificationsEnabled) Icons.Default.Notifications
                    else Icons.Default.NotificationsOff,
                    contentDescription = "Notification settings",
                    tint = if (notificationsEnabled) colors.neonCyan else colors.textMuted,
                    modifier = Modifier.size(22.sdp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.sdp))

        if (todayWord != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.neonCyan.copy(alpha = 0.12f),
                                colors.surfaceVariant.copy(alpha = 0.7f),
                            )
                        )
                    )
                    .border(1.sdp, colors.neonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.sdp))
                    .clickable { onWordClick(todayWord.id) }
                    .padding(16.sdp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = todayWord.word,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = colors.neonCyan,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                            if (todayWord.partOfSpeech.isNotEmpty()) {
                                Text(
                                    text = todayWord.partOfSpeech,
                                    style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.sdp))
                                    .background(levelColorOf(todayWord.level).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.sdp, vertical = 2.sdp)
                            ) {
                                Text(
                                    text = todayWord.level,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = levelColorOf(todayWord.level),
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.sdp))
                    Text(
                        text = todayWord.meaning,
                        style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.sdp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No daily word yet. Enable notifications to get words!",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
                )
            }
        }

        val previousWords = recentWords.drop(1).take(2)
        if (previousWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.sdp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.sdp))
                    .clickable { onAllLearnedWordsClick() }
                    .padding(vertical = 4.sdp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Previous Words",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "See all →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.neonCyan,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(8.sdp))

            previousWords.forEach { word ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.sdp))
                        .clickable { onWordClick(word.id) }
                        .padding(vertical = 8.sdp, horizontal = 4.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.sdp)
                            .clip(CircleShape)
                            .background(
                                if (word.isRead) colors.textMuted.copy(alpha = 0.3f)
                                else colors.neonCyan,
                            )
                    )
                    Spacer(modifier = Modifier.width(10.sdp))
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.sdp))
                            .background(levelColorOf(word.level).copy(alpha = 0.15f))
                            .padding(horizontal = 6.sdp, vertical = 2.sdp)
                    ) {
                        Text(
                            text = word.level,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = levelColorOf(word.level),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.ssp,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.sdp))
                    Text(
                        text = word.sentDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.textMuted,
                            fontSize = 10.ssp,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    currentHour: Int,
    currentMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val timePickerState = rememberTimePickerState(
        initialHour = currentHour,
        initialMinute = currentMinute,
        is24Hour = false,
    )
    var showKeyboard by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.sdp),
            color = colors.surface,
            tonalElevation = 6.sdp,
        ) {
            Column(
                modifier = Modifier.padding(start = 24.sdp, end = 24.sdp, top = 24.sdp, bottom = 12.sdp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Select notification time",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.sdp),
                )
                if (showKeyboard) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.sdp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showKeyboard = !showKeyboard }) {
                        Icon(
                            imageVector = if (showKeyboard) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                            contentDescription = if (showKeyboard) "Switch to clock" else "Switch to keyboard",
                            tint = colors.textSecondary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.sdp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = colors.textMuted)
                        }
                        TextButton(onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) }) {
                            Text("Save", color = colors.neonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerTopicsRow() {
    val colors = LocalSpeakMindColors.current
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.sdp),
        horizontalArrangement = Arrangement.spacedBy(14.sdp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(200.sdp)
                    .height(200.sdp)
                    .clip(RoundedCornerShape(20.sdp))
                    .background(colors.surfaceVariant.copy(alpha = shimmerAlpha))
            ) {
                Column(modifier = Modifier.padding(16.sdp)) {
                    // Icon placeholder
                    Box(
                        modifier = Modifier
                            .size(32.sdp)
                            .clip(RoundedCornerShape(8.sdp))
                            .background(colors.textMuted.copy(alpha = shimmerAlpha * 0.5f))
                    )
                    Spacer(modifier = Modifier.height(16.sdp))
                    // Title placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(16.sdp)
                            .clip(RoundedCornerShape(4.sdp))
                            .background(colors.textMuted.copy(alpha = shimmerAlpha * 0.5f))
                    )
                    Spacer(modifier = Modifier.height(8.sdp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.sdp)
                            .clip(RoundedCornerShape(4.sdp))
                            .background(colors.textMuted.copy(alpha = shimmerAlpha * 0.5f))
                    )
                    Spacer(modifier = Modifier.height(12.sdp))
                    // Category placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.sdp)
                            .clip(RoundedCornerShape(4.sdp))
                            .background(colors.textMuted.copy(alpha = shimmerAlpha * 0.3f))
                    )
                }
            }
        }
    }
}
