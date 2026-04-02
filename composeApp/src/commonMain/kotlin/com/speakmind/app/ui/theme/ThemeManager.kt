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

/**
 * Theme preference: null = follow system, true = dark, false = light.
 */
class ThemeManager(private val database: SpeakyDatabase) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _darkModeOverride = MutableStateFlow<Boolean?>(null)
    val darkModeOverride: StateFlow<Boolean?> = _darkModeOverride.asStateFlow()

    fun load() {
        val progress = database.speakMindQueries.selectProgress().executeAsOneOrNull()
        val stored = progress?.dark_mode ?: -1L
        _darkModeOverride.value = when (stored) {
            0L -> false
            1L -> true
            else -> null // -1 or any other = follow system
        }
    }

    fun toggle(currentlyDark: Boolean) {
        val newIsDark = !currentlyDark
        _darkModeOverride.value = newIsDark  // immediate UI update on calling thread
        val dbValue = if (newIsDark) 1L else 0L
        scope.launch { database.speakMindQueries.updateDarkMode(dbValue) }
    }
}
