package com.speakmind.app.feature.community.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.ai.domain.NameExtractor
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.feature.profile.domain.NameValidationResult
import com.speakmind.app.feature.profile.domain.NameValidator
import com.speakmind.app.navigation.CommunityHubDestination
import com.speakmind.app.navigation.HomeDestination
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommunitySetupUiState(
    val nickname: String = "",
    val gender: String = "",
    val isNicknameFromOnboarding: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Photo picked from the gallery (JPEG), uploaded when joining. */
    val photoBytes: ByteArray? = null,
    /** Google profile photo, used when no photo is picked. */
    val googlePhotoUrl: String? = null,
    /** "I'm 13+ and agree to the community rules" — required to join. */
    val acceptedTerms: Boolean = false,
    val error: String? = null,
)

class CommunitySetupViewModel(
    private val communityRepository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunitySetupUiState())
    val uiState: StateFlow<CommunitySetupUiState> = _uiState.asStateFlow()

    init {
        checkExistingProfile()
    }

    private fun checkExistingProfile() {
        viewModelScope.launch {
            if (!communityRepository.hasSession() || communityRepository.isGuest()) {
                // Not signed in: open Speaky Hub read-only; chatting asks for Google sign-in.
                openHub()
                return@launch
            }
            val profile = try { communityRepository.getLocalProfile() } catch (_: Exception) { null }
            if (profile != null && profile.gender.isNotEmpty()) {
                openHub()
                return@launch
            }
            val savedNickname = communityRepository.authDisplayName()?.take(30)
                ?: communityRepository.getUserNickname()
            _uiState.value = _uiState.value.copy(
                nickname = savedNickname,
                isNicknameFromOnboarding = savedNickname.isNotEmpty(),
                googlePhotoUrl = communityRepository.authPhotoUrl(),
                isLoading = false,
            )
        }
    }

    fun onNicknameChanged(value: String) {
        if (value.length > 30) return
        val extracted = NameExtractor.extract(value)
        val resolved = if (extracted != null) extracted else capitalizeName(value)
        val error = if (resolved.trim().length >= 2) {
            NameValidator.errorMessage(NameValidator.validate(resolved.trim()))
        } else null
        _uiState.value = _uiState.value.copy(nickname = resolved, error = error)
    }

    fun onPhotoPicked(bytes: ByteArray?) {
        if (bytes == null) return
        viewModelScope.launch {
            // Checked on the phone: an immodest photo isn't used.
            if (com.speakmind.app.feature.community.moderation.PhotoSafety.check(bytes)?.immodest == true) {
                _uiState.value = _uiState.value.copy(photoBytes = null, error = PHOTO_NOT_ALLOWED)
            } else {
                _uiState.value = _uiState.value.copy(photoBytes = bytes, error = null)
            }
        }
    }

    fun onGenderSelected(gender: String) {
        _uiState.value = _uiState.value.copy(gender = gender, error = null)
    }

    fun onTermsToggled() {
        _uiState.value = _uiState.value.copy(acceptedTerms = !_uiState.value.acceptedTerms, error = null)
    }

    fun onJoinClicked() {
        val state = _uiState.value
        val nickname = state.nickname.trim()
        val validationResult = NameValidator.validate(nickname)
        if (validationResult != NameValidationResult.Valid) {
            _uiState.value = state.copy(
                error = NameValidator.errorMessage(validationResult) ?: "Invalid name"
            )
            return
        }
        if (state.gender.isEmpty()) {
            _uiState.value = state.copy(error = "Please select your gender")
            return
        }
        if (!state.acceptedTerms) {
            _uiState.value = state.copy(error = "Please confirm you're 13+ and agree to the community rules")
            return
        }
        _uiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                communityRepository.saveUserProfile(nickname, state.gender)
                state.photoBytes?.let { bytes ->
                    try {
                        communityRepository.uploadAvatar(bytes)
                    } catch (e: Exception) {
                        // Joining shouldn't fail because of the photo; it can be changed later in Edit profile.
                        Napier.w("Avatar upload failed", e)
                    }
                }
                openHub()
            } catch (e: Exception) {
                Napier.e("CommunitySetup join failed", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = friendlyCommunityError(e, "Failed to join. Check your connection."),
                )
            }
        }
    }

    /** Opens a fresh community home on top of Home, clearing read-only screens left from before sign-in. */
    private fun openHub() {
        navigationManager.navigate(CommunityHubDestination) {
            popUpTo<HomeDestination> { inclusive = false }
            launchSingleTop = true
        }
    }
}

private fun capitalizeName(value: String): String =
    value.split(" ").joinToString(" ") { word ->
        if (word.isEmpty()) word else word.replaceFirstChar { it.uppercase() }
    }


/** Shown when a picked profile photo isn't modest. */
const val PHOTO_NOT_ALLOWED = "This photo isn't allowed. Please choose a modest photo, or keep the default avatar."
