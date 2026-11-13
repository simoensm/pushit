package com.pushitup.core.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pushitup.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUpdateUiState(
    /** Non-null when a newer version is available on the Play Store. */
    val availableVersion     : RemoteVersionInfo? = null,
    /** Changelog to show in the What's New dialog (non-null on first launch after update). */
    val whatsNewChangelog    : AppChangelog.VersionChangelog? = null,
    val updateBannerDismissed: Boolean = false,
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updateRepo : AppUpdateRepository,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    val uiState: StateFlow<AppUpdateUiState> get() = _uiState
    private val _uiState = MutableStateFlow(AppUpdateUiState())

    init {
        checkWhatsNew()
        checkForUpdate()
    }

    // ── What's New (shown once after the user installs a new version) ──────────

    private fun checkWhatsNew() {
        viewModelScope.launch {
            val lastSeen = ctx.updateStore.lastSeenChangelogVersionFlow().first()
            val current  = BuildConfig.VERSION_CODE
            if (current > lastSeen) {
                val changelog = AppChangelog.entries[current]
                if (changelog != null) {
                    _uiState.update { it.copy(whatsNewChangelog = changelog) }
                } else {
                    // No entry for this version — mark as seen so we don't check again
                    ctx.updateStore.markChangelogSeen(current)
                }
            }
        }
    }

    fun dismissWhatsNew() {
        viewModelScope.launch {
            ctx.updateStore.markChangelogSeen(BuildConfig.VERSION_CODE)
            _uiState.update { it.copy(whatsNewChangelog = null) }
        }
    }

    // ── Update available banner ────────────────────────────────────────────────

    private fun checkForUpdate() {
        viewModelScope.launch {
            val remote = updateRepo.fetchLatestVersion().getOrNull() ?: return@launch
            if (remote.latestVersionCode > BuildConfig.VERSION_CODE) {
                _uiState.update { it.copy(availableVersion = remote) }
                // Cache so the WorkManager notification won't fire for a version we already warned about
                ctx.updateStore.saveLatestVersion(remote.latestVersionCode)
            }
        }
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateBannerDismissed = true) }
    }
}
