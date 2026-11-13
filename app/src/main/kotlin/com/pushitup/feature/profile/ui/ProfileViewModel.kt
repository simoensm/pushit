package com.pushitup.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pushitup.domain.model.*
import com.pushitup.domain.repository.AuthRepository
import com.pushitup.domain.repository.UserRepository
import com.pushitup.domain.repository.WorkoutRepository
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile             : UserProfile?          = null,
    val isLoading           : Boolean               = true,
    val isSaving            : Boolean               = false,
    val isUploadingPhoto    : Boolean               = false,
    val error               : String?               = null,
    val saveSuccess         : Boolean               = false,
    val unlockedAchievements: List<Achievement>     = emptyList(),
    val lockedAchievements  : List<Achievement>     = emptyList(),
    /** Tags from unlocked achievements that are available to equip (not yet equipped). */
    val availableTags           : List<String>      = emptyList(),
    /** Achievements unlocked but whose star reward has not been claimed yet. */
    val claimableAchievements   : List<Achievement> = emptyList(),
    val weeklyLogs          : List<DailyLog>        = emptyList(),
    val bestSessionReps     : Int                   = 0,
    val recentSessions      : List<WorkoutSession>  = emptyList(),
    // Search
    val searchQuery         : String            = "",
    val searchResults       : List<UserProfile> = emptyList(),
    val isSearching         : Boolean           = false,
    val viewingProfile           : UserProfile? = null,  // public profile in dialog
    val viewingProfileTodayReps  : Int          = 0,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepo    : AuthRepository,
    private val userRepo    : UserRepository,
    private val workoutRepo : WorkoutRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> get() = _uiState
    private val _uiState = MutableStateFlow(ProfileUiState())

    // Internal search query flow with debounce
    private val _searchQuery = MutableStateFlow("")

    // Jobs for per-user Firestore listeners — cancelled and re-started on each login
    private var userJobs: List<Job> = emptyList()

    init {
        // React to auth state: set up listeners when a user logs in, clear when they log out
        authRepo.authStateFlow()
            .distinctUntilChanged()
            .onEach { uid ->
                userJobs.forEach { it.cancel() }
                userJobs = emptyList()
                if (uid != null) {
                    _uiState.update { it.copy(isLoading = true) }
                    // Seed Room with recent Firestore daily logs so streak calculation
                    // and the weekly chart work correctly on a new/fresh device.
                    viewModelScope.launch {
                        workoutRepo.seedLocalLogsFromFirestore(uid, days = 14)
                    }
                    userJobs = setupForUser(uid)
                } else {
                    _uiState.update { ProfileUiState() }
                }
            }
            .launchIn(viewModelScope)

        observeSearch()
    }

    private fun setupForUser(uid: String): List<Job> {
        val j1 = userRepo.profileFlow(uid)
            .onEach { profile ->
                val best     = _uiState.value.bestSessionReps
                val unlocked = AllAchievements.filter { profile != null && isUnlocked(it, profile, best) }
                val locked   = AllAchievements.filter { profile == null || !isUnlocked(it, profile, best) }
                val equipped = profile?.equippedTags ?: emptyList()
                val claimed  = profile?.claimedAchievements ?: emptyList()
                _uiState.update {
                    it.copy(
                        profile                  = profile,
                        isLoading                = false,
                        unlockedAchievements     = unlocked,
                        lockedAchievements       = locked,
                        availableTags            = unlocked.mapNotNull { a -> a.tag }.filter { t -> t !in equipped },
                        claimableAchievements    = unlocked.filter { a -> a.id !in claimed },
                    )
                }
            }
            .launchIn(viewModelScope)

        val j2 = workoutRepo.observeWeeklyLogs(uid, LocalDate.now().minusDays(6))
            .onEach { logs -> _uiState.update { it.copy(weeklyLogs = logs) } }
            .launchIn(viewModelScope)

        val j3 = workoutRepo.observeSessionsForUser(uid)
            .map { sessions -> sessions.sortedByDescending { it.startedAt }.take(15) }
            .onEach { recent -> _uiState.update { it.copy(recentSessions = recent) } }
            .launchIn(viewModelScope)

        val j4 = viewModelScope.launch {
            val best = workoutRepo.getPersonalBestReps(uid)
            _uiState.update { state ->
                val profile  = state.profile
                val unlocked = AllAchievements.filter { profile != null && isUnlocked(it, profile, best) }
                val locked   = AllAchievements.filter { profile == null || !isUnlocked(it, profile, best) }
                val equipped = profile?.equippedTags ?: emptyList()
                state.copy(
                    bestSessionReps      = best,
                    unlockedAchievements = unlocked,
                    lockedAchievements   = locked,
                    availableTags        = unlocked.mapNotNull { a -> a.tag }.filter { t -> t !in equipped },
                )
            }
        }

        return listOf(j1, j2, j3, j4)
    }

    private fun observeSearch() {
        _searchQuery
            .debounce(350)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.length < 2) {
                    _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                    return@onEach
                }
                _uiState.update { it.copy(isSearching = true) }
                val result = userRepo.searchByUsername(query.lowercase().trim())
                val myUid  = authRepo.currentUid
                _uiState.update {
                    it.copy(
                        isSearching   = false,
                        searchResults = result.getOrDefault(emptyList()).filter { p -> p.uid != myUid },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectPublicProfile(profile: UserProfile) {
        _uiState.update { it.copy(viewingProfile = profile, viewingProfileTodayReps = 0) }
        viewModelScope.launch {
            val reps = userRepo.getTodayReps(profile.uid)
            _uiState.update { it.copy(viewingProfileTodayReps = reps) }
        }
    }

    fun clearPublicProfile() {
        _uiState.update { it.copy(viewingProfile = null, viewingProfileTodayReps = 0) }
    }

    fun sendFriendRequest(toUid: String) {
        val myUid = authRepo.currentUid ?: return
        viewModelScope.launch {
            userRepo.sendFriendRequest(myUid, toUid)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    // ── Profile edits ─────────────────────────────────────────────────────────

    fun updateDisplayName(newName: String) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = userRepo.updateProfile(uid, mapOf("displayName" to newName.trim()))
            _uiState.update {
                it.copy(
                    isSaving    = false,
                    saveSuccess = result.isSuccess,
                    error       = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun updateDailyGoal(goal: Int) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            userRepo.updateProfile(uid, mapOf("dailyGoal" to goal.toLong()))
        }
    }

    fun updateHeight(heightCm: Float?) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            if (heightCm != null) userRepo.updateProfile(uid, mapOf("height" to heightCm.toDouble()))
        }
    }

    fun updateWeight(weightKg: Float?) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            if (weightKg != null) userRepo.updateProfile(uid, mapOf("weight" to weightKg.toDouble()))
        }
    }


    fun signOut() {
        viewModelScope.launch { authRepo.signOut() }
    }

    fun uploadPhoto(imageBytes: ByteArray) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, error = null) }
            val result = userRepo.uploadPhoto(uid, imageBytes)
            _uiState.update {
                it.copy(
                    isUploadingPhoto = false,
                    error = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun removePhoto() {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, error = null) }
            val result = userRepo.clearPhoto(uid)
            _uiState.update {
                it.copy(
                    isUploadingPhoto = false,
                    error = if (result.isFailure) result.exceptionOrNull()?.localizedMessage else null,
                )
            }
        }
    }

    fun clearError()       = _uiState.update { it.copy(error = null) }
    fun clearSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }

    // ── Tag equip / unequip ───────────────────────────────────────────────────

    fun claimAchievement(achievement: Achievement) {
        val uid     = authRepo.currentUid ?: return
        val claimed = _uiState.value.profile?.claimedAchievements ?: emptyList()
        if (achievement.id in claimed) return
        viewModelScope.launch {
            val newClaimed = claimed + achievement.id
            userRepo.updateProfile(
                uid,
                mapOf(
                    "claimedAchievements" to newClaimed,
                    "stars"               to com.google.firebase.firestore.FieldValue.increment(achievement.starReward.toLong()),
                ),
            )
        }
    }

    fun equipTag(tag: String) {
        val uid     = authRepo.currentUid ?: return
        val current = _uiState.value.profile?.equippedTags ?: emptyList()
        if (current.size >= 3 || tag in current) return
        viewModelScope.launch {
            userRepo.updateProfile(uid, mapOf("equippedTags" to (current + tag)))
        }
    }

    fun unequipTag(tag: String) {
        val uid     = authRepo.currentUid ?: return
        val current = _uiState.value.profile?.equippedTags ?: emptyList()
        viewModelScope.launch {
            userRepo.updateProfile(uid, mapOf("equippedTags" to (current - tag)))
        }
    }

    // ── Achievement check ─────────────────────────────────────────────────────

    private fun isUnlocked(a: Achievement, profile: UserProfile, bestSessionReps: Int): Boolean =
        when (val c = a.condition) {
            is AchievementCondition.TotalReps     -> profile.totalReps >= c.count
            is AchievementCondition.Streak        -> profile.longestStreak >= c.days
            is AchievementCondition.FriendsCount  -> profile.friends.size >= c.count
            is AchievementCondition.SingleSession -> bestSessionReps >= c.count
        }
}
