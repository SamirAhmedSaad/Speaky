package com.speakmind.app.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Switches read from the server's `app_settings` table once per app start (in the same request as
 * the ads switch), so changing them on the server reaches every app without an update.
 */
object ServerSettings {
    private val _coursesVersion = MutableStateFlow<Long?>(null)

    /**
     * Bump `courses_version` on the server to make every app re-download its courses. Null until read;
     * the course list reloads when it arrives (it can come after the list first loaded from the saved copy).
     */
    val coursesVersionFlow: StateFlow<Long?> = _coursesVersion.asStateFlow()

    var coursesVersion: Long?
        get() = _coursesVersion.value
        set(value) {
            _coursesVersion.value = value
        }

    /** `ads_off`: no ads for anyone. */
    @Volatile
    var adsOff: Boolean = false
}
