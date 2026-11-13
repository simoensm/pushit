package com.pushitup.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pushitup.domain.model.UserProfile
import com.pushitup.domain.repository.AuthRepository
import com.pushitup.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading         : Boolean = false,
    val error             : String? = null,
    val success           : Boolean = false,
    /**
     * Set when a new user needs the profile-photo setup screen.
     * - Google sign-up: also pre-fills username/displayName from Google.
     * - Email sign-up: username/displayName already chosen; just pick a photo.
     */
    val needsProfileSetup         : Boolean = false,
    val setupSuggestedUsername    : String  = "",
    val setupSuggestedDisplayName : String  = "",
    val setupGooglePhotoUrl       : String? = null,
    /** Whether this is a Google sign-up (true) or email sign-up (false). */
    val setupIsGoogleSignUp       : Boolean = false,
    /** UID kept around so we can create the profile once the user confirms. */
    val pendingUid                : String  = "",
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<AuthUiState> get() = _uiState
    private val _uiState = MutableStateFlow(AuthUiState())

    /**
     * Phase 1 of email sign-up: validate username, create Firebase Auth account,
     * then route to the photo-setup screen. The Firestore profile is created in phase 2
     * ([completeEmailSetup]) once the user has picked (or skipped) a photo.
     */
    fun signUp(email: String, password: String, username: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val trimmedUsername = username.lowercase().trim()
                if (trimmedUsername.length < 3) {
                    _uiState.update { it.copy(isLoading = false, error = "Username must be at least 3 characters") }
                    return@launch
                }
                if (!trimmedUsername.matches(Regex("[a-z0-9_.]+"))) {
                    _uiState.update { it.copy(isLoading = false, error = "Username can only contain letters, numbers, _ and .") }
                    return@launch
                }
                val taken = runCatching { userRepo.isUsernameTaken(trimmedUsername) }.getOrElse { e ->
                    _uiState.update { it.copy(isLoading = false, error = "Could not verify username: ${e.localizedMessage}") }
                    return@launch
                }
                if (taken) {
                    _uiState.update { it.copy(isLoading = false, error = "Username already taken") }
                    return@launch
                }

                val authResult = authRepo.signUp(email, password)
                if (authResult.isFailure) {
                    _uiState.update { it.copy(isLoading = false, error = authResult.exceptionOrNull()?.localizedMessage) }
                    return@launch
                }
                val uid = authResult.getOrThrow()

                // Route to photo-pick step; Firestore profile created after.
                _uiState.update {
                    it.copy(
                        isLoading                = false,
                        needsProfileSetup        = true,
                        setupIsGoogleSignUp      = false,
                        pendingUid               = uid,
                        setupSuggestedUsername   = trimmedUsername,
                        setupSuggestedDisplayName = displayName.trim().ifBlank { trimmedUsername },
                        setupGooglePhotoUrl      = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Unexpected error") }
            }
        }
    }

    /**
     * Phase 2 of email sign-up: create the Firestore profile with the chosen photo bytes.
     * [imageBytes] is null when the user keeps the default letter avatar.
     */
    fun completeEmailSetup(imageBytes: ByteArray?) {
        val uid         = _uiState.value.pendingUid
        val username    = _uiState.value.setupSuggestedUsername
        val displayName = _uiState.value.setupSuggestedDisplayName
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Upload photo if the user chose one; ignore failure (letter avatar fallback)
                val photoUrl: String? = if (imageBytes != null) {
                    userRepo.uploadPhoto(uid, imageBytes).getOrNull()
                } else null

                val profile = UserProfile(
                    uid            = uid,
                    username       = username,
                    displayName    = displayName,
                    photoUrl       = photoUrl,
                    photoUpdatedAt = if (photoUrl != null) System.currentTimeMillis() else 0L,
                    stars          = 0L,
                    totalReps      = 0L,
                    currentStreak  = 0,
                    longestStreak  = 0,
                    friends        = emptyList(),
                    height         = null,
                    weight         = null,
                )
                val profileResult = userRepo.createProfile(profile)
                if (profileResult.isFailure) {
                    _uiState.update { it.copy(isLoading = false, error = "Profile save failed: ${profileResult.exceptionOrNull()?.localizedMessage}") }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = false, needsProfileSetup = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Setup failed") }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = authRepo.signIn(email, password)
                if (result.isFailure) {
                    _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.localizedMessage) }
                } else {
                    _uiState.update { it.copy(isLoading = false, success = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Unexpected error") }
            }
        }
    }

    /**
     * Phase 1 of Google sign-in:
     * - Existing users  → success immediately (go to dashboard).
     * - New users       → emit [needsProfileSetup] so the UI shows the profile-setup screen
     *                     with fields pre-filled from Google, editable by the user.
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val authResult = authRepo.signInWithGoogle(idToken)
                if (authResult.isFailure) {
                    _uiState.update { it.copy(isLoading = false, error = authResult.exceptionOrNull()?.localizedMessage) }
                    return@launch
                }
                val uid = authResult.getOrThrow()

                // Returning user — profile already exists.
                val existing = userRepo.getProfile(uid).getOrNull()
                if (existing != null) {
                    // Username missing or too short → force setup screen to fix it.
                    if (existing.username.length >= 3) {
                        _uiState.update { it.copy(isLoading = false, success = true) }
                        return@launch
                    }
                    val googleName = existing.displayName.ifBlank {
                        authRepo.currentDisplayName()?.trim() ?: "Athlete"
                    }
                    val suggestedUsername = existing.username.ifBlank {
                        googleName.lowercase().replace(Regex("[^a-z0-9]"), "").take(16).ifBlank { "athlete" }
                    }
                    _uiState.update {
                        it.copy(
                            isLoading                 = false,
                            needsProfileSetup         = true,
                            setupIsGoogleSignUp       = true,
                            pendingUid                = uid,
                            setupSuggestedUsername    = suggestedUsername,
                            setupSuggestedDisplayName = googleName,
                            setupGooglePhotoUrl       = existing.photoUrl ?: authRepo.currentPhotoUrl(),
                        )
                    }
                    return@launch
                }

                // New user — pre-fill suggestions from Google and let them confirm.
                val googleName  = authRepo.currentDisplayName()?.trim() ?: "Athlete"
                val googlePhoto = authRepo.currentPhotoUrl()
                val suggestedUsername = googleName
                    .lowercase()
                    .replace(Regex("[^a-z0-9]"), "")
                    .take(16)
                    .ifBlank { "athlete" }

                _uiState.update {
                    it.copy(
                        isLoading                 = false,
                        needsProfileSetup         = true,
                        setupIsGoogleSignUp       = true,
                        pendingUid                = uid,
                        setupSuggestedUsername    = suggestedUsername,
                        setupSuggestedDisplayName = googleName,
                        setupGooglePhotoUrl       = googlePhoto,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Google sign-in failed") }
            }
        }
    }

    /**
     * Phase 2 of Google sign-up: confirm/edit username + display name, pick photo source.
     *
     * @param useLetterAvatar  True → ignore Google photo, use letter initial.
     * @param imageBytes       Optional custom photo picked from gallery (overrides Google URL).
     */
    fun completeGoogleSetup(
        username        : String,
        displayName     : String,
        useLetterAvatar : Boolean    = false,
        imageBytes      : ByteArray? = null,
    ) {
        val uid         = _uiState.value.pendingUid
        val googlePhoto = _uiState.value.setupGooglePhotoUrl
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val trimmed = username.lowercase().trim()
                if (trimmed.length < 3) {
                    _uiState.update { it.copy(isLoading = false, error = "Username must be at least 3 characters") }
                    return@launch
                }
                if (!trimmed.matches(Regex("[a-z0-9_.]+"))) {
                    _uiState.update { it.copy(isLoading = false, error = "Username can only contain letters, numbers, _ and .") }
                    return@launch
                }
                // excludeUid: returning users keeping their own username must not be blocked
                val taken = runCatching { userRepo.isUsernameTaken(trimmed, excludeUid = uid) }.getOrElse { false }
                if (taken) {
                    _uiState.update { it.copy(isLoading = false, error = "Username already taken") }
                    return@launch
                }

                // Resolve final photo URL: custom upload > Google URL > none
                val photoUrl: String? = when {
                    useLetterAvatar    -> null
                    imageBytes != null -> userRepo.uploadPhoto(uid, imageBytes).getOrNull()
                    else               -> googlePhoto
                }

                val profile = UserProfile(
                    uid            = uid,
                    username       = username.lowercase().trim(),
                    displayName    = displayName.trim().ifBlank { username },
                    photoUrl       = photoUrl,
                    photoUpdatedAt = if (photoUrl != null) System.currentTimeMillis() else 0L,
                    stars          = 0L,
                    totalReps      = 0L,
                    currentStreak  = 0,
                    longestStreak  = 0,
                    friends        = emptyList(),
                    height         = null,
                    weight         = null,
                )
                val profileResult = userRepo.createProfile(profile)
                if (profileResult.isFailure) {
                    _uiState.update { it.copy(isLoading = false, error = "Profile save failed: ${profileResult.exceptionOrNull()?.localizedMessage}") }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = false, needsProfileSetup = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Setup failed") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
