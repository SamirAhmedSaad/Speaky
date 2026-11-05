package com.speakmind.app.ui.theme

import com.speakmind.app.db.SpeakyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App themes; the gold ones are for Premium members. */
enum class AppTheme(val label: String, val premium: Boolean) {
    System("System", false),
    Violet("Light", false),
    Dark("Dark", false),
    GoldLuxe("Gold Luxe", true),
    RoseGold("Rose Gold", true),
}

/**
 * The chosen theme, kept in progress.dark_mode: -1 system, 0 light, 1 dark, 2 Gold Luxe, 3 Rose Gold.
 * +100 records that Premium already switched this user to gold once, so a later pick sticks.
 */
class ThemeManager(private val database: SpeakyDatabase) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _theme = MutableStateFlow(AppTheme.System)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private var goldOffered = false

    fun load() {
        val stored = database.speakMindQueries.selectProgress().executeAsOneOrNull()?.dark_mode ?: -1L
        goldOffered = stored >= 100
        _theme.value = decode(if (goldOffered) stored - 100 else stored)
    }

    fun set(theme: AppTheme) {
        _theme.value = theme
        save()
    }

    /** Onboarding's moon/sun button. */
    fun toggle(currentlyDark: Boolean) = set(if (currentlyDark) AppTheme.Violet else AppTheme.Dark)

    /** First time we see an active Premium (new purchase or after updating): switch to Gold Luxe once. */
    fun onPremiumActive() {
        if (goldOffered) return
        goldOffered = true
        _theme.value = AppTheme.GoldLuxe
        save()
    }

    private fun save() {
        val code = encode(_theme.value) + if (goldOffered) 100 else 0
        scope.launch { database.speakMindQueries.updateDarkMode(code) }
    }

    private fun decode(code: Long) = when (code) {
        0L -> AppTheme.Violet
        1L -> AppTheme.Dark
        2L -> AppTheme.GoldLuxe
        3L -> AppTheme.RoseGold
        else -> AppTheme.System
    }

    private fun encode(theme: AppTheme): Long = when (theme) {
        AppTheme.System -> -1L
        AppTheme.Violet -> 0L
        AppTheme.Dark -> 1L
        AppTheme.GoldLuxe -> 2L
        AppTheme.RoseGold -> 3L
    }
}
