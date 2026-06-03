package com.speakmind.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.speakmind.app.feature.aisetup.ui.aiSetupScreen
import com.speakmind.app.feature.article.ui.articleDetailScreen
import com.speakmind.app.feature.chat.ui.chatScreen
import com.speakmind.app.feature.community.ui.chat.channelScreen
import com.speakmind.app.feature.community.ui.setup.communitySetupScreen
import com.speakmind.app.feature.vocabgroup.ui.myGroupsScreen
import com.speakmind.app.feature.vocabgroup.ui.groupDetailScreen
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

    val darkModeOverride by themeManager.darkModeOverride.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = darkModeOverride ?: systemDark

    SpeakMindTheme(darkTheme = isDark) {
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
            wordLookupScreen()
            privacyPolicyScreen(onBack = { navController.popBackStack() })
            communitySetupScreen()
            channelScreen()
            myGroupsScreen()
            groupDetailScreen()
        }
    }
}
