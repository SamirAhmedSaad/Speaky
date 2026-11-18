package com.speakmind.app.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App updates: Google Play says whether a newer version exists for this phone; the server's
 * `app_settings` decide how it is offered:
 *  - `update_min_version_code`: versions below it MUST update (full-screen, can't be skipped).
 *  - `update_optional_on`: show the small "update available" banner to everyone else (default on).
 *  - `update_message`: optional text shown with the prompt.
 *  - `update_prompts_on`: master switch (admins, from the Hub's ADMIN menu). Off by default; off = no
 *    prompts at all, and missing or unreadable also counts as off.
 *
 * Nobody is ever blocked unless Play actually offers the update to them, so a slow rollout, an
 * install from outside Play, no Play services or no network never lock anyone out.
 */
object AppUpdate {

    sealed interface Ui {
        data object None : Ui

        /** Optional update: banner with "Update" and a close button. */
        data object Available : Ui

        /** Optional update downloading in the background. [percent] is 0..100. */
        data class Downloading(val percent: Int) : Ui

        /** Downloaded: one tap restarts into the new version. */
        data object ReadyToInstall : Ui

        /** Mandatory: full screen, no way around it except updating. */
        data object Required : Ui
    }

    /** What the platform (Google Play on Android) can do. Null on platforms without in-app updates. */
    interface Platform {
        /** Mandatory update: Google Play's full-screen update. */
        fun startRequired()

        /** Optional update: download in the background. */
        fun startOptional()

        /** Restart into the downloaded update. */
        fun installDownloaded()
    }

    var platform: Platform? = null

    private val _ui = MutableStateFlow<Ui>(Ui.None)
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    /** Server settings, refreshed on every app start/resume. */
    @kotlin.concurrent.Volatile var minVersionCode: Long? = null
    @kotlin.concurrent.Volatile var optionalOn: Boolean = true
    // Off until the server says otherwise: an admin must turn update prompts on.
    private val _enabled = MutableStateFlow(false)

    /** Debug test mode (simulated Play): ignore the server's on/off so the flow can be tried. */
    var forceEnabledForTest = false
    /** Master switch: when off, no update prompt is shown (mandatory or optional). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** The banner was closed in this app session: don't show it again until the next start. */
    private var optionalDismissed = false

    fun applyServerSettings(minVersionCode: Long?, optionalOn: Boolean, message: String?, enabled: Boolean = false) {
        this.minVersionCode = minVersionCode
        this.optionalOn = optionalOn
        _message.value = message?.takeIf { it.isNotBlank() }
        setEnabled(enabled)
    }

    /** Turning the feature off removes any prompt on screen at once (a mandatory one included). */
    fun setEnabled(on: Boolean) {
        _enabled.value = on || forceEnabledForTest
        if (!on && _ui.value != Ui.None) _ui.value = Ui.None
    }

    fun setUi(ui: Ui) {
        _ui.value = if (_enabled.value) ui else Ui.None
    }

    fun dismissOptional() {
        if (_ui.value == Ui.Available) {
            optionalDismissed = true
            _ui.value = Ui.None
        }
    }

    val isOptionalDismissed: Boolean get() = optionalDismissed

    fun onUpdateClicked() {
        when (_ui.value) {
            Ui.Required -> platform?.startRequired()
            Ui.Available -> platform?.startOptional()
            Ui.ReadyToInstall -> platform?.installDownloaded()
            else -> Unit
        }
    }
}

/** What to do about an update, from what Play reports and the server's settings. */
enum class UpdateDecision { None, Optional, Required }

fun decideUpdate(
    enabled: Boolean,
    currentVersionCode: Long,
    minVersionCode: Long?,
    optionalOn: Boolean,
    playHasUpdate: Boolean,
    immediateAllowed: Boolean,
    flexibleAllowed: Boolean,
): UpdateDecision {
    if (!enabled || !playHasUpdate) return UpdateDecision.None
    val mustUpdate = minVersionCode != null && minVersionCode > 0 && currentVersionCode < minVersionCode
    return when {
        mustUpdate && immediateAllowed -> UpdateDecision.Required
        // Play won't run its full-screen flow (rare): still offer the update rather than nothing.
        mustUpdate && flexibleAllowed -> UpdateDecision.Optional
        optionalOn && flexibleAllowed -> UpdateDecision.Optional
        else -> UpdateDecision.None
    }
}
