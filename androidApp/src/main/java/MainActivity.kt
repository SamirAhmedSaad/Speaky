package com.speakmind.app

import androidx.compose.ui.graphics.luminance
import com.speakmind.app.feature.community.ui.components.openPrivateChat
import com.speakmind.app.navigation.AdminReportsDestination
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
import com.speakmind.app.ads.AdConfig
import com.speakmind.app.ads.AdManager
import com.speakmind.app.premium.BillingManager
import com.speakmind.app.config.ServerSettings
import com.speakmind.app.premium.Premium
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.ActiveRoomTracker
import com.speakmind.app.feature.community.data.model.SPEAKY_HUB_ROOM_ID
import com.speakmind.app.feature.community.platform.ChatMessagingService
import com.speakmind.app.feature.dailyword.platform.DailyWordAlarmReceiver
import com.speakmind.app.feature.vocabulary.domain.VocabRefreshService
import com.speakmind.app.feature.voice.platform.MicPermissionRequester
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.OnboardingDestination
import com.speakmind.app.navigation.RoomDestination
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

        AdConfig.useTestAds = BuildConfig.DEBUG
        // Privacy signals are set inside AdManager (non-personalized ads where consent would be needed).
        AdManager.init(this)
        BillingManager.init(this)
        // Server-granted ad-free (admins): last known answer first, refreshed in onStart.
        getSharedPreferences("premium", MODE_PRIVATE).let { prefs ->
            Premium.setServerAdFree(prefs.getBoolean("server_ad_free", false))
            Premium.setAdsOff(prefs.getBoolean("ads_off", false))
        }

        // Device tests: adb shell am start -n <pkg>/.MainActivity --ez simulate_purchase true (debuggable builds only).
        if (intent?.getBooleanExtra("simulate_purchase", false) == true) Premium.simulatePurchaseForTesting()
        // Device tests: --ez seed_fun_avatars true uploads the fun avatar images once (debuggable builds only).
        if (intent?.getBooleanExtra("seed_fun_avatars", false) == true && com.speakmind.app.platform.isDebugMode) {
            lifecycleScope.launch {
                val repo = getKoin().get<CommunityRepository>()
                repo.hasSession()
                val FunAvatars = com.speakmind.app.feature.community.ui.components.FunAvatars
                FunAvatars.keys.forEach { key ->
                    runCatching { repo.uploadPublicImage("${FunAvatars.STORAGE_FOLDER}/$key.png", FunAvatars.bytesOf(key)) }
                        .onFailure { android.util.Log.w("SeedFunAvatars", "$key failed", it) }
                        .onSuccess { android.util.Log.i("SeedFunAvatars", "$key uploaded") }
                }
            }
        }

        setContent {
            val themeManager = getKoin().get<ThemeManager>()
            val theme by themeManager.theme.collectAsState()
            val premium by Premium.active.collectAsState()
            val systemDark = isSystemInDarkTheme()
            // Status bar icons follow the theme in use (Gold Luxe is dark; a lapsed gold pick falls back to light).
            val isDark = when (theme) {
                com.speakmind.app.ui.theme.AppTheme.System -> systemDark
                com.speakmind.app.ui.theme.AppTheme.Dark -> true
                com.speakmind.app.ui.theme.AppTheme.GoldLuxe -> premium
                else -> false
            }
            // A coloured header (the Hub) gets light icons when its colour is dark.
            val tint = com.speakmind.app.ui.components.StatusBarTint.color.value
            val darkStatusBar = tint?.let { it.luminance() < 0.5f } ?: isDark

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkStatusBar)
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
        handleChatIntent(intent)
        ChatMessagingService.registerCurrentToken()
        lifecycleScope.launch {
            // Opening Speaky Hub clears its (growing) notification.
            ActiveRoomTracker.activeRoomId.collect { roomId ->
                if (roomId == SPEAKY_HUB_ROOM_ID) ChatMessagingService.clearHubNotification(this@MainActivity)
                // Opening a private chat (from the list or anywhere) clears its notification too.
                else if (roomId != null) ChatMessagingService.clearChatNotification(this@MainActivity, roomId)
            }
        }
    }

    private var countedOnline = false

    /** Signed-in community members show as online while the app is open (not only on chat screens). */
    override fun onStart() {
        super.onStart()
        // Purchase sheet needs the visible activity; also re-checks the subscription (cancelled/renewed).
        BillingManager.instance?.attach(this)
        AdManager.instance?.onAppForeground()
        lifecycleScope.launch {
            val repo = getKoin().get<CommunityRepository>()
            if (repo.hasSession() && !repo.isGuest() && !countedOnline) {
                countedOnline = true
                repo.setActive(true)
            }
            // Ad-free is decided on the server only; keep the last answer when offline.
            repo.isAdFree()?.let { adFree ->
                // Only write when something changed: every pending write is flushed on the main thread
                // when a service stops, and frequent no-op writes made that ANR more likely.
                val prefs = getSharedPreferences("premium", MODE_PRIVATE)
                if (!prefs.contains("server_ad_free") || prefs.getBoolean("server_ad_free", false) != adFree ||
                    prefs.getBoolean("ads_off", false) != ServerSettings.adsOff
                ) {
                    prefs.edit()
                        .putBoolean("server_ad_free", adFree)
                        .putBoolean("ads_off", ServerSettings.adsOff)
                        .apply()
                }
                Premium.setServerAdFree(adFree)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AdManager.instance?.onAppBackground()
        if (countedOnline) {
            countedOnline = false
            getKoin().get<CommunityRepository>().setActive(false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDailyWordIntent(intent)
        handleChatIntent(intent)
    }

    /** Opens the chat a community notification was about. */
    private fun handleChatIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ChatMessagingService.EXTRA_OPEN_REPORTS, false) == true) {
            intent.removeExtra(ChatMessagingService.EXTRA_OPEN_REPORTS)
            if (!isFreshNotificationTap(intent)) return
            lifecycleScope.launch { getKoin().get<NavigationManager>().navigateWhenReady(AdminReportsDestination) }
            return
        }
        val roomId = intent?.getStringExtra(ChatMessagingService.EXTRA_ROOM_ID) ?: return
        val title = intent.getStringExtra(ChatMessagingService.EXTRA_ROOM_TITLE).orEmpty()
        val callId = intent.getStringExtra(ChatMessagingService.EXTRA_CALL_ID)
        intent.removeExtra(ChatMessagingService.EXTRA_ROOM_ID)
        intent.removeExtra(ChatMessagingService.EXTRA_CALL_ID)
        if (!isFreshNotificationTap(intent)) return
        // Voice call invitation: open the Hub; if the call already closed, say so there.
        if (callId != null) getKoin().get<com.speakmind.app.feature.community.call.HubCallManager>().openedFromInvite(callId)
        val open = {
            lifecycleScope.launch {
                getKoin().get<NavigationManager>().openChatWhenReady(RoomDestination(roomId, title))
            }
            Unit
        }
        // Private chats: Premium, or free private messages left today (same rule as opening them in the app).
        if (roomId == SPEAKY_HUB_ROOM_ID) open()
        else lifecycleScope.launch { getKoin().get<CommunityRepository>().openPrivateChat("Private messages", open) }
    }

    /**
     * Android re-delivers the task's original intent when the app restarts (from recents or after
     * being killed), which would reopen a chat from an old notification. Each tap carries an id;
     * only ids newer than the last one handled are acted on.
     */
    private fun isFreshNotificationTap(intent: Intent): Boolean {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return false
        val tapId = intent.getLongExtra(ChatMessagingService.EXTRA_TAP_ID, 0L)
        val prefs = getSharedPreferences("notification_taps", MODE_PRIVATE)
        if (tapId <= prefs.getLong("last_chat_tap", 0L)) return false
        prefs.edit().putLong("last_chat_tap", tapId).apply()
        return true
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
        intent.removeExtra(DailyWordAlarmReceiver.EXTRA_WORD)
        // Reopened from recents: the original alarm intent is re-delivered, don't jump to the word again.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        Napier.d { "Opening daily word from notification: $word" }
        lifecycleScope.launch {
            try {
                getKoin().get<NavigationManager>().navigateWhenReady(WordDetailDestination(word = word))
            } catch (e: Exception) {
                Napier.e { "Failed to navigate to daily word: ${e.message}" }
            }
        }
    }


}
