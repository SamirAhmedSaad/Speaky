package com.speakmind.app

import com.speakmind.app.feature.dailyword.ui.previousWordsScreen
import com.speakmind.app.feature.community.call.adminCallsScreen
import com.speakmind.app.feature.community.admin.adminSubscribersScreen
import com.speakmind.app.feature.community.call.hubCallScreen
import com.speakmind.app.feature.games.ui.gamesScreen
import com.speakmind.app.feature.games.ui.idiomBuilderScreen
import com.speakmind.app.feature.games.ui.idiomRushScreen
import com.speakmind.app.feature.games.ui.wordGameScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.speakmind.app.ui.components.AppBannerHost
import com.speakmind.app.premium.PaywallHost
import com.speakmind.app.premium.RemoveAdsHost
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.speakmind.app.feature.aisetup.ui.aiSetupScreen
import com.speakmind.app.feature.article.ui.articleDetailScreen
import com.speakmind.app.feature.chat.ui.chatScreen
import com.speakmind.app.feature.community.ui.admin.adminChatsScreens
import com.speakmind.app.feature.community.ui.admin.adminReportsScreen
import com.speakmind.app.feature.community.ui.hub.communityHubScreen
import com.speakmind.app.feature.community.ui.people.peopleScreen
import com.speakmind.app.feature.community.ui.room.roomChatScreen
import com.speakmind.app.feature.community.ui.setup.communitySetupScreen
import com.speakmind.app.feature.vocabgroup.ui.myGroupsScreen
import com.speakmind.app.feature.vocabgroup.ui.groupDetailScreen
import com.speakmind.app.feature.course.ui.courseBookScreen
import com.speakmind.app.feature.course.ui.courseScreen
import com.speakmind.app.feature.course.ui.coursesScreen
import com.speakmind.app.feature.course.ui.lessonExamScreen
import com.speakmind.app.feature.course.ui.lessonScreen
import com.speakmind.app.feature.dailyword.ui.wordDetailScreen
import com.speakmind.app.feature.wordlookup.ui.wordLookupScreen
import com.speakmind.app.feature.download.ui.modelDownloadScreen
import com.speakmind.app.feature.flashcard.ui.flashcardReviewScreen
import com.speakmind.app.feature.home.ui.homeScreen
import com.speakmind.app.feature.legal.ui.privacyPolicyScreen
import com.speakmind.app.feature.onboarding.ui.onboardingScreen
import com.speakmind.app.feature.story.ui.storiesScreen
import com.speakmind.app.feature.story.ui.storyDetailScreen
import com.speakmind.app.feature.vocabulary.ui.vocabCategoryScreen
import com.speakmind.app.feature.vocabulary.ui.vocabWordListScreen
import com.speakmind.app.navigation.NavCommand
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.SpeakMindTheme
import com.speakmind.app.ui.theme.ThemeManager
import com.speakmind.app.ui.theme.AppTheme
import com.speakmind.app.ui.theme.TtsSpeedManager
import org.koin.compose.koinInject

@Composable
fun AppRoot(startDestination: Any) {
    val themeManager = koinInject<ThemeManager>()
    val ttsSpeedManager = koinInject<TtsSpeedManager>()

    LaunchedEffect(Unit) {
        themeManager.load()
        ttsSpeedManager.load()
    }

    val chosenTheme by themeManager.theme.collectAsState()
    val premium by com.speakmind.app.premium.Premium.active.collectAsState()
    LaunchedEffect(premium) { if (premium) themeManager.onPremiumActive() }
    val systemDark = isSystemInDarkTheme()
    // Gold themes need an active Premium; when it lapses the app goes back to the normal look.
    val appTheme = when {
        chosenTheme.premium && !premium -> AppTheme.Violet
        chosenTheme == AppTheme.System -> if (systemDark) AppTheme.Dark else AppTheme.Violet
        else -> chosenTheme
    }

    SpeakMindTheme(appTheme = appTheme) {
        val navController = rememberNavController()
        val navigationManager = koinInject<NavigationManager>()

        LaunchedEffect(navigationManager) {
            navigationManager.commands.collect { command ->
                when (command) {
                    is NavCommand.To -> {
                        navController.navigate(command.destination) {
                            command.navOptions?.invoke(this)
                        }
                    }
                    is NavCommand.Back -> {
                        navController.popBackStack()
                    }
                    is NavCommand.PopUpTo -> {
                        navController.popBackStack(command.destination, command.inclusive)
                    }
                    is NavCommand.ClearStackAndNavigate -> {
                        navController.navigate(command.destination) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .background(LocalSpeakMindColors.current.backgroundDark)
        ) {
            onboardingScreen()
            homeScreen()
            aiSetupScreen()
            modelDownloadScreen()
            chatScreen()
            flashcardReviewScreen()
            storiesScreen()
            storyDetailScreen()
            articleDetailScreen()
            vocabCategoryScreen()
            vocabWordListScreen()
            wordDetailScreen()
            previousWordsScreen()
            gamesScreen()
            idiomRushScreen()
            idiomBuilderScreen()
            wordGameScreen()
            wordLookupScreen()
            privacyPolicyScreen(onBack = { navController.popBackStack() })
            communitySetupScreen()
            communityHubScreen()
            peopleScreen()
            adminReportsScreen()
            adminChatsScreens()
            roomChatScreen()
            myGroupsScreen()
            groupDetailScreen()
            coursesScreen()
            courseScreen()
            courseBookScreen()
            lessonScreen()
            lessonExamScreen()
            hubCallScreen()
            adminCallsScreen()
            adminSubscribersScreen()
        }
        // Every screen scrolls edge to edge; this keeps content from running under the clock and battery icons.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(com.speakmind.app.ui.components.StatusBarTint.color.value ?: MaterialTheme.colorScheme.background)
        )
        // One banner for the whole app, kept loaded; screens with a BannerAdView make it visible.
        AppBannerHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        // "Remove ads or watch the ad" before full-screen ads
        // Hub voice call: floating pill while minimized, "call ended" card and short messages.
        com.speakmind.app.feature.community.call.HubCallOverlay(
            onOpenCall = { navigationManager.navigate(com.speakmind.app.feature.community.call.HubCallDestination) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        RemoveAdsHost()
        PaywallHost()
        }
    }
}
