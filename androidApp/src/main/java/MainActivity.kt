package com.speakmind.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.dailyword.platform.DailyWordAlarmReceiver
import com.speakmind.app.feature.vocabulary.domain.VocabRefreshService
import com.speakmind.app.feature.voice.platform.MicPermissionRequester
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.OnboardingDestination
import com.speakmind.app.navigation.WordDetailDestination
import com.speakmind.app.ui.theme.ThemeManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.getKoin

class MainActivity : ComponentActivity() {

    private val startDestination = MutableStateFlow<Any?>(null)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Napier.d { "Microphone permission granted: $isGranted" }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Napier.d { "Notification permission granted: $isGranted" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { startDestination.value == null }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = getKoin().get<SpeakyDatabase>()
            db.speakMindQueries.insertDefaultProgress()
            val info = packageManager.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
            getKoin().get<VocabRefreshService>().refreshIfUpdated(versionCode)
            val userName = db.speakMindQueries.selectProgress().executeAsOneOrNull()?.user_name ?: ""
            withContext(Dispatchers.Main) {
                startDestination.value = if (userName.isEmpty()) OnboardingDestination else HomeDestination
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        getKoin().get<MicPermissionRequester>().register(micPermissionLauncher)
        requestNotificationPermission()

        setContent {
            val themeManager = getKoin().get<ThemeManager>()
            val darkModeOverride by themeManager.darkModeOverride.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = darkModeOverride ?: systemDark

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark)
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (isDark)
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                )
            }

            val dest by startDestination.collectAsState()
            dest?.let { AppRoot(startDestination = it) }
        }

        handleDailyWordIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDailyWordIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleDailyWordIntent(intent: Intent?) {
        val word = intent?.getStringExtra(DailyWordAlarmReceiver.EXTRA_WORD) ?: return
        Napier.d { "Opening daily word from notification: $word" }
        try {
            val navigationManager = getKoin().get<NavigationManager>()
            navigationManager.navigate(WordDetailDestination(word = word))
        } catch (e: Exception) {
            Napier.e { "Failed to navigate to daily word: ${e.message}" }
        }
    }


}
