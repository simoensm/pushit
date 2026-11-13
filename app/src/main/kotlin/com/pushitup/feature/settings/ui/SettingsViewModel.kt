package com.pushitup.feature.settings.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.pushitup.core.notification.NotificationPrefs
import com.pushitup.core.notification.NotificationPrefsRepository
import com.pushitup.core.notification.NotificationScheduler
import androidx.datastore.preferences.core.edit
import com.pushitup.core.onboarding.OnboardingKeys
import com.pushitup.core.onboarding.onboardingStore
import com.pushitup.core.theme.ThemeMode
import com.pushitup.core.theme.setThemeMode
import com.pushitup.core.theme.themeModeFlow
import com.pushitup.core.theme.themeStore
import com.pushitup.domain.repository.AuthRepository
import com.pushitup.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot navigation events emitted by [SettingsViewModel]. */
enum class SettingsNavEvent { NavigateToLogin }

data class SettingsUiState(
    val currentEmail          : String          = "",
    val selectedLanguage      : String          = "en",   // "en" or "fr"
    val themeMode             : ThemeMode       = ThemeMode.SYSTEM,
    val notifPrefs            : NotificationPrefs = NotificationPrefs(),
    val isLoading             : Boolean         = false,
    val success               : String?         = null,
    val error                 : String?         = null,
    val isAdmin               : Boolean         = false,
    val isGoogleUser          : Boolean         = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepo      : AuthRepository,
    private val userRepo      : UserRepository,
    private val notifPrefsRepo: NotificationPrefsRepository,
    private val scheduler     : NotificationScheduler,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    private val _navEvent = MutableSharedFlow<SettingsNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<SettingsNavEvent> = _navEvent.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> get() = _uiState
    private val _uiState = MutableStateFlow(run {
        val email = authRepo.currentEmail() ?: ""
        SettingsUiState(
            currentEmail     = email,
            selectedLanguage = resolveCurrentLanguage(),
            isAdmin          = isAdminEmail(email.lowercase()),
            isGoogleUser     = authRepo.isGoogleUser(),
        )
    })

    init {
        // Observe notification prefs from DataStore
        notifPrefsRepo.prefsFlow
            .onEach { prefs -> _uiState.update { it.copy(notifPrefs = prefs) } }
            .launchIn(viewModelScope)

        // Observe theme preference
        ctx.themeStore.themeModeFlow()
            .onEach { mode -> _uiState.update { it.copy(themeMode = mode) } }
            .launchIn(viewModelScope)

        // Detect admin — username "admin" OR the developer's own email (fallback if profile not yet set)
        val email = authRepo.currentEmail()?.lowercase().orEmpty()
        _uiState.update { it.copy(isAdmin = isAdminEmail(email)) }
        val uid = authRepo.currentUid
        if (uid != null) {
            userRepo.profileFlow(uid)
                .onEach { profile ->
                    _uiState.update { st ->
                        st.copy(isAdmin = profile?.username == "admin" || isAdminEmail(email))
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { ctx.themeStore.setThemeMode(mode) }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    fun setLanguage(tag: String) {
        val localeList = if (tag == "fr") {
            LocaleListCompat.forLanguageTags("fr")
        } else {
            LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(localeList)
        _uiState.update { it.copy(selectedLanguage = tag) }
    }

    // ── Notification toggles ──────────────────────────────────────────────────

    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notifPrefsRepo.setDailyReminderEnabled(enabled)
            if (enabled) {
                val prefs = _uiState.value.notifPrefs
                scheduler.scheduleDailyReminder(prefs.dailyReminderHour, prefs.dailyReminderMinute)
            } else {
                scheduler.cancelDailyReminder()
            }
        }
    }

    fun setDailyReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            notifPrefsRepo.setDailyReminderTime(hour, minute)
            // Re-schedule with new time if enabled
            if (_uiState.value.notifPrefs.dailyReminderEnabled) {
                scheduler.scheduleDailyReminder(hour, minute)
            }
        }
    }

    fun setLeaderboardNotif(enabled: Boolean) {
        viewModelScope.launch {
            notifPrefsRepo.setLeaderboardNotif(enabled)
            if (enabled) scheduler.scheduleLeaderboardCheck()
            else         scheduler.cancelLeaderboardCheck()
        }
    }

    fun setChallengeDeadlineNotif(enabled: Boolean) {
        viewModelScope.launch {
            notifPrefsRepo.setChallengeDeadlineNotif(enabled)
            if (enabled) scheduler.scheduleChallengeDeadlineCheck()
            else         scheduler.cancelChallengeDeadlineCheck()
        }
    }

    // ── Email update (requires re-auth first) ─────────────────────────────────

    fun updateEmail(newEmail: String, currentPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = null) }

            val reauth = authRepo.reauthenticate(currentPassword)
            if (reauth.isFailure) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Incorrect password: ${reauth.exceptionOrNull()?.localizedMessage}")
                }
                return@launch
            }

            val result = authRepo.updateEmail(newEmail)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, success = "Verification email sent to $newEmail. Check your inbox.")
                } else {
                    it.copy(isLoading = false, error = result.exceptionOrNull()?.localizedMessage ?: "Failed to update email")
                }
            }
        }
    }

    // ── Password update (requires re-auth first) ──────────────────────────────

    fun updatePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = null) }

            val reauth = authRepo.reauthenticate(currentPassword)
            if (reauth.isFailure) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Incorrect password: ${reauth.exceptionOrNull()?.localizedMessage}")
                }
                return@launch
            }

            val result = authRepo.updatePassword(newPassword)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, success = "Password updated successfully.")
                } else {
                    it.copy(isLoading = false, error = result.exceptionOrNull()?.localizedMessage ?: "Failed to update password")
                }
            }
        }
    }

    // ── Sign out ──────────────────────────────────────────────────────────────

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()
            _navEvent.emit(SettingsNavEvent.NavigateToLogin)
        }
    }

    // ── Delete account ────────────────────────────────────────────────────────

    /** For email/password users: re-auth with password then delete. */
    fun deleteAccount(currentPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val reauth = authRepo.reauthenticate(currentPassword)
            if (reauth.isFailure) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Incorrect password: ${reauth.exceptionOrNull()?.localizedMessage}")
                }
                return@launch
            }
            performDelete()
        }
    }

    /** For Google users: re-auth with a fresh Google ID token then delete. */
    fun deleteAccountGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val reauth = authRepo.reauthenticateGoogle(idToken)
            if (reauth.isFailure) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Google re-authentication failed: ${reauth.exceptionOrNull()?.localizedMessage}")
                }
                return@launch
            }
            performDelete()
        }
    }

    private suspend fun performDelete() {
        val result = authRepo.deleteAccount()
        if (result.isSuccess) {
            _navEvent.emit(SettingsNavEvent.NavigateToLogin)
        } else {
            _uiState.update {
                it.copy(isLoading = false, error = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete account")
            }
        }
    }

    // ── Test notification ─────────────────────────────────────────────────────

    /** Fires an immediate notification so the user can verify permissions are working. */
    fun sendTestNotification() {
        com.pushitup.core.notification.postNotification(
            ctx       = ctx,
            channelId = com.pushitup.core.notification.NotificationChannels.DAILY_REMINDER,
            notifId   = 9999,
            title     = ctx.getString(com.pushitup.R.string.settings_notif_test_title),
            body      = ctx.getString(com.pushitup.R.string.settings_notif_test_body),
        )
    }

    // ── Onboarding tour ───────────────────────────────────────────────────────

    fun resetTour() {
        viewModelScope.launch {
            ctx.onboardingStore.edit { it[OnboardingKeys.TOUR_COMPLETE] = false }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(success = null, error = null) }
}

/** Emails that are granted admin access to the hidden developer panel. */
private fun isAdminEmail(email: String): Boolean =
    email == "admin@pushit.com"

/** Read current locale set via AppCompatDelegate; default to "en". */
private fun resolveCurrentLanguage(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return "en"
    val tag = locales[0]?.language ?: return "en"
    return if (tag == "fr") "fr" else "en"
}
