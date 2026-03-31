package com.speakmind.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.speakmind.app.feature.chat.ui.chatScreen
import com.speakmind.app.feature.download.ui.modelDownloadScreen
import com.speakmind.app.feature.flashcard.ui.flashcardReviewScreen
import com.speakmind.app.feature.home.ui.homeScreen
import com.speakmind.app.feature.splash.ui.splashScreen
import com.speakmind.app.navigation.NavCommand
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.SplashDestination
import com.speakmind.app.ui.theme.SpeakMindColors
import com.speakmind.app.ui.theme.SpeakMindTheme
import org.koin.compose.koinInject

@Composable
fun AppRoot() {
    SpeakMindTheme {
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
            startDestination = SplashDestination,
            modifier = Modifier
                .fillMaxSize()
                .background(SpeakMindColors.backgroundDark)
        ) {
            splashScreen()
            homeScreen()
            modelDownloadScreen()
            chatScreen()
            flashcardReviewScreen()
        }
    }
}
