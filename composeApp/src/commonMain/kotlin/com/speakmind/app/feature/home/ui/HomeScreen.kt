package com.speakmind.app.feature.home.ui

import com.speakmind.app.ui.components.rememberInterstitialAdState
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
import com.speakmind.app.ui.icons.CommunityChatIcon
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import com.speakmind.app.feature.course.ui.CoursesHomeViewModel
import com.speakmind.app.premium.Premium
import com.speakmind.app.ui.components.MainTab
import com.speakmind.app.ui.components.MainTabs
import com.speakmind.app.ui.components.SpeakyBottomBar

fun NavGraphBuilder.homeScreen() {
    animatedComposable<HomeDestination> {
        val viewModel = koinViewModel<HomeViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val gate = rememberInterstitialAdState()

        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshFlashcardCount()
                    viewModel.refreshCommunityUnread()
                    viewModel.refreshDailyWords()
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
            onScenarioClick = { id -> gate.unlock("article:$id", "Unlock this article") { viewModel.onScenarioClicked(id) } },
            onCloudChatClick = { gate.unlock("speaky-chat", "Unlock Speaky Chat") { viewModel.onCloudChatClicked() } },
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
            onMyGroupsClick = viewModel::onMyGroupsClicked,
            onAllLearnedWordsClick = viewModel::onAllLearnedWordsClicked,
            onCommunityClick = viewModel::onCommunityClicked,
            onPrivacyPolicyClick = viewModel::onPrivacyPolicyClicked,
            onExactAlarmRationaleDismissed = viewModel::onExactAlarmRationaleDismissed,
            onExactAlarmRationaleConfirmed = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    exactAlarmSettingsLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    )
                }
                viewModel.onExactAlarmRationaleDismissed()
            },
            onNameClick = viewModel::onNameClicked,
            onNameSheetDismissed = viewModel::onNameSheetDismissed,
            onNameSheetInputChanged = viewModel::onNameSheetInputChanged,
            onNameSheetSaved = viewModel::onNameSheetSaved,
            tabs = {
                HomeTabsHost(
                    viewModel = viewModel,
                    uiState = uiState,
                    onScenarioClick = { id -> gate.unlock("article:$id", "Unlock this article") { viewModel.onScenarioClicked(id) } },
                    onCloudChatClick = { gate.unlock("speaky-chat", "Unlock Speaky Chat") { viewModel.onCloudChatClicked() } },
                )
            },
        )
    }
}

/** Home's four tabs, the floating bar, the banner above it and the Settings layer. */
@Composable
private fun HomeTabsHost(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    onScenarioClick: (String) -> Unit,
    onCloudChatClick: () -> Unit,
) {
    val coursesViewModel = koinViewModel<CoursesHomeViewModel>()
    val courses by coursesViewModel.cards.collectAsStateWithLifecycle()
    LifecycleResumeEffect(coursesViewModel) {
        coursesViewModel.refresh()
        onPauseOrDispose {}
    }
    val tab by MainTabs.selected.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val ttsSpeed by viewModel.ttsSpeed.collectAsStateWithLifecycle()
    val isPremium by Premium.active.collectAsStateWithLifecycle()
    val onlineCount by viewModel.onlineCount.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    BackHandler(enabled = showSettings || tab != MainTab.Home) {
        if (showSettings) showSettings = false else MainTabs.selected.value = MainTab.Home
    }

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            MainTab.Home, MainTab.Chat -> HomeTab(
                state = uiState,
                courses = courses,
                onlineCount = onlineCount,
                onSettingsClick = { showSettings = true },
                onLevelClick = viewModel::onLevelBadgeClicked,
                onCourseClick = { coursesViewModel.onContinueCourse(it.course) },
                onAllCoursesClick = coursesViewModel::onAllCoursesClicked,
                onReviewClick = viewModel::onReviewClicked,
                onHubClick = viewModel::onHubClicked,
                onWordClick = viewModel::onDailyWordClicked,
                onSpeakWord = viewModel::onSpeakTodayWord,
                onTopicClick = onScenarioClick,
                onAllTopicsClick = { MainTabs.selected.value = MainTab.Learn },
            )
            MainTab.Learn -> LearnTab(
                state = uiState,
                courses = courses,
                showStories = com.speakmind.app.config.Features.STORIES,
                showAiChat = com.speakmind.app.config.Features.AI_CHAT,
                onLevelSelected = viewModel::onLevelSelected,
                onCourseClick = { coursesViewModel.onCourseClicked(it.course) },
                onContinueClick = { coursesViewModel.onContinueCourse(it.course) },
                onAllCoursesClick = coursesViewModel::onAllCoursesClicked,
                onTopicClick = onScenarioClick,
                onStoriesClick = viewModel::onStoriesClicked,
                onAiChatClick = onCloudChatClick,
            )
            MainTab.Words -> WordsTab(
                state = uiState,
                showGroups = com.speakmind.app.config.Features.MY_GROUPS,
                onLookupClick = viewModel::onWordLookupClicked,
                onReviewClick = viewModel::onReviewClicked,
                onWordBuilderClick = viewModel::onVocabularyClicked,
                onSavedClick = viewModel::onAllLearnedWordsClicked,
                onGroupsClick = viewModel::onMyGroupsClicked,
                onWordClick = viewModel::onDailyWordClicked,
                onAllWordsClick = viewModel::onPreviousWordsClicked,
                onReminderClick = viewModel::onTimePickerClicked,
            )
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            BannerAdView(Modifier.fillMaxWidth())
            val gamesWaiting by com.speakmind.app.feature.games.data.GamesBadge.waiting.collectAsStateWithLifecycle()
            SpeakyBottomBar(
                selected = tab,
                chatBadge = uiState.communityUnread,
                gamesWaiting = gamesWaiting,
                onGames = viewModel::onGamesClicked,
                onSelect = { selected ->
                    if (selected == MainTab.Chat) viewModel.onCommunityClicked()
                    else MainTabs.selected.value = selected
                },
            )
        }

        if (showSettings) {
            SettingsLayer(
                state = uiState,
                theme = theme,
                ttsSpeed = ttsSpeed,
                isPremium = isPremium,
                appVersion = com.speakmind.app.platform.rememberAppVersion(),
                onBack = { showSettings = false },
                onNameClick = viewModel::onNameClicked,
                onLevelClick = viewModel::onLevelBadgeClicked,
                onPremiumClick = {
                    if (!isPremium) Premium.requirePremium("Speaky Premium", "No ads, unlimited private chat and a gold badge.") {}
                },
                onThemeSelected = { picked ->
                    if (picked.premium && !isPremium) {
                        Premium.requirePremium("Gold themes are for Premium", "Unlock Gold Luxe and Rose Gold with Speaky Premium.") {
                            viewModel.onThemeSelected(picked)
                        }
                    } else viewModel.onThemeSelected(picked)
                },
                onReminderClick = viewModel::onTimePickerClicked,
                onSpeedSelected = viewModel::onTtsSpeedSelected,
                onPrivacyClick = viewModel::onPrivacyPolicyClicked,
                onContactClick = { runCatching { uriHandler.openUri("mailto:samir.hayan@gmail.com") } },
            )
        }
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
    onMyGroupsClick: () -> Unit = {},
    onAllLearnedWordsClick: () -> Unit,
    onCommunityClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onExactAlarmRationaleDismissed: () -> Unit = {},
    onExactAlarmRationaleConfirmed: () -> Unit = {},
    onNameClick: () -> Unit = {},
    onNameSheetDismissed: () -> Unit = {},
    onNameSheetInputChanged: (String) -> Unit = {},
    onNameSheetSaved: () -> Unit = {},
    tabs: @Composable () -> Unit = {},
) {
    val colors = LocalSpeakMindColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        if (uiState.showLevelPicker) {
            LevelSheet(
                currentLevel = uiState.userLevel,
                onLevelSelected = onLevelSelected,
                onDismiss = onLevelPickerDismissed,
            )
        }

        if (uiState.showTimePicker) {
            ReminderSheet(
                hour = uiState.notificationHour,
                minute = uiState.notificationMinute,
                enabled = uiState.notificationsEnabled,
                onToggle = onNotificationToggled,
                onSave = onNotificationTimeChanged,
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

        // Name update bottom sheet
        if (uiState.showNameSheet) {
            NameUpdateBottomSheet(
                input = uiState.nameSheetInput,
                error = uiState.nameSheetError,
                onInputChanged = onNameSheetInputChanged,
                onSave = onNameSheetSaved,
                onDismiss = onNameSheetDismissed,
            )
        }

        tabs()
    }
}











@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameUpdateBottomSheet(
    input: String,
    error: String?,
    onInputChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.sdp)
                    .width(36.sdp)
                    .height(4.sdp)
                    .clip(RoundedCornerShape(2.sdp))
                    .background(colors.textMuted.copy(alpha = 0.4f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.sdp)
                .padding(bottom = 32.sdp),
            verticalArrangement = Arrangement.spacedBy(16.sdp),
        ) {
            Text(
                text = "Update your name",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                label = { Text("Name") },
                placeholder = { Text("e.g. Sara") },
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.neonCyan,
                    focusedLabelColor = colors.neonCyan,
                    cursorColor = colors.neonCyan,
                    unfocusedTextColor = colors.textPrimary,
                    focusedTextColor = colors.textPrimary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.sdp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.sdp, colors.textMuted.copy(alpha = 0.4f)),
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.neonCyan),
                ) {
                    Text("Save", color = colors.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
