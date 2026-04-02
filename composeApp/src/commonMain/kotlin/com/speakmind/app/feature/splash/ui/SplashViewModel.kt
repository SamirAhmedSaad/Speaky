package com.speakmind.app.feature.splash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.OnboardingDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashViewModel(
    private val navigationManager: NavigationManager,
    private val database: SpeakyDatabase,
) : ViewModel() {

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            delay(800)
            database.speakMindQueries.insertDefaultProgress()
            database.speakMindQueries.migrateAiEngineValues()
            val userName = database.speakMindQueries.selectProgress().executeAsOneOrNull()?.user_name ?: ""
            if (userName.isEmpty()) {
                navigationManager.clearStackAndNavigate(OnboardingDestination)
            } else {
                navigationManager.clearStackAndNavigate(HomeDestination)
            }
        }
    }
}
