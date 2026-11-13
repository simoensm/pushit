package com.pushitup.feature.dashboard.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pushitup.domain.model.*
import com.pushitup.domain.repository.UserRepository
import com.pushitup.domain.repository.WorkoutRepository
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ActiveChallengeDisplay(
    val challenge : Challenge,
    val progress  : Float,
    val label     : String,
)

data class DashboardUiState(
    val displayName      : String                      = "",
    val stars            : Long                        = 0L,
    val tier             : RankTier?                   = null,
    val nextTier         : RankTier?                   = null,
    val rankProgress     : Float                       = 0f,
    val todayReps        : Int                         = 0,
    val dailyGoal        : Int                         = 100,
    val currentStreak    : Int                         = 0,
    val weeklyLogs       : List<DailyLog>              = emptyList(),
    val activeChallenges : List<ActiveChallengeDisplay> = emptyList(),
    val friendsRanking   : List<FriendRankEntry>       = emptyList(),
    val rankingLoading   : Boolean                     = false,
    val isLoading        : Boolean                     = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val userRepository   : UserRepository,
    private val auth             : FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        checkInactivityPenalty()
        loadFriendsRanking()
    }

    /**
     * Runs once per calendar day (guarded by a SharedPrefs flag).
     * Subtracts stars for each full day the user skipped since their last workout.
     * Applies an extra flat penalty if they've been gone a full week or more.
     */
    private fun checkInactivityPenalty() {
        val uid = auth.currentUser?.uid ?: return   // not signed in — skip
        val today = LocalDate.now()
        val prefs = context.getSharedPreferences("pushit_prefs", Context.MODE_PRIVATE)
        val lastChecked = prefs.getString("last_penalty_date", null)?.let { LocalDate.parse(it) }

        if (lastChecked == today) return             // already ran today

        viewModelScope.launch {
            try {
                val lastWorkout = workoutRepository.getLastWorkoutDate(uid)
                if (lastWorkout != null) {
                    // missedDays = full calendar days between last workout and today,
                    // minus 1 because "today" hasn't ended yet.
                    val missedDays = ChronoUnit.DAYS.between(lastWorkout, today).toInt() - 1
                    if (missedDays >= 1) {
                        val daily  = missedDays * RankSystem.PENALTY_PER_MISSED_DAY
                        val extra  = if (missedDays >= 7) RankSystem.PENALTY_EXTRA_WEEK else 0L
                        val total  = daily + extra
                        userRepository.applyStarPenalty(uid, total)
                        Log.d(TAG, "Inactivity: $missedDays missed days → -$total ★")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inactivity penalty check failed", e)
            } finally {
                prefs.edit().putString("last_penalty_date", today.toString()).apply()
            }
        }
    }

    companion object { private const val TAG = "DashboardVM" }

    /** Fetches ranking on open and refreshes every 5 minutes while the screen is visible. */
    private fun loadFriendsRanking() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(rankingLoading = true) }
                val result = userRepository.getFriendsWithDailyReps(uid, LocalDate.now())
                _uiState.update {
                    it.copy(
                        friendsRanking = result.getOrDefault(emptyList()),
                        rankingLoading = false,
                    )
                }
                delay(5 * 60 * 1_000L) // refresh every 5 min
            }
        }
    }

    /** Called from the UI when the user manually pulls to refresh the ranking. */
    fun refreshRanking() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(rankingLoading = true) }
            val result = userRepository.getFriendsWithDailyReps(uid, LocalDate.now())
            _uiState.update {
                it.copy(
                    friendsRanking = result.getOrDefault(emptyList()),
                    rankingLoading = false,
                )
            }
        }
    }

    private fun loadDashboard() {
        val uid   = auth.currentUser?.uid ?: "local_user"
        val today = LocalDate.now()

        // Today's rep count
        workoutRepository.observeDailyLog(uid, today)
            .onEach { log -> _uiState.update { it.copy(todayReps = log?.totalReps ?: 0) } }
            .launchIn(viewModelScope)

        // Weekly chart
        workoutRepository.observeWeeklyLogs(uid, today.minusDays(6))
            .onEach { logs ->
                _uiState.update { it.copy(weeklyLogs = logs, isLoading = false) }
            }
            .launchIn(viewModelScope)

        // Profile: stars + rank + name + streak (Firestore is authoritative for streak)
        userRepository.profileFlow(uid)
            .filterNotNull()
            .onEach { profile ->
                val tier = RankSystem.tierFor(profile.stars)
                _uiState.update {
                    it.copy(
                        displayName   = profile.displayName.ifBlank { profile.username },
                        stars         = profile.stars,
                        tier          = tier,
                        nextTier      = RankSystem.nextTier(profile.stars),
                        rankProgress  = RankSystem.progressToNext(profile.stars),
                        currentStreak = profile.currentStreak,
                        dailyGoal     = profile.dailyGoal,
                    )
                }
            }
            .launchIn(viewModelScope)

        // Active challenges
        combine(
            userRepository.profileFlow(uid),
            userRepository.activeChallengesFlow(uid),
        ) { profile, userChallenges ->
            if (profile == null) return@combine emptyList()
            val byId = userChallenges.associateBy { it.challengeId }
            AllChallenges.mapNotNull { c ->
                val uc = byId[c.id] ?: return@mapNotNull null
                if (uc.completed) return@mapNotNull null
                val expired = System.currentTimeMillis() - uc.startedAt > c.durationDays * 86_400_000L
                if (expired) return@mapNotNull null

                val progress = computeProgress(c, uc, profile)
                val label    = buildLabel(c, uc, profile)
                ActiveChallengeDisplay(c, progress, label)
            }
        }
        .onEach { active -> _uiState.update { it.copy(activeChallenges = active) } }
        .launchIn(viewModelScope)
    }

    private fun computeProgress(c: Challenge, uc: UserChallenge, profile: UserProfile): Float {
        return when (c.type) {
            ChallengeType.REPS_TOTAL -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                (earned.toFloat() / c.target).coerceIn(0f, 1f)
            }
            ChallengeType.DAILY_REPS -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                (earned.toFloat() / (c.target * c.targetDays)).coerceIn(0f, 1f)
            }
            ChallengeType.STREAK -> (profile.currentStreak.toFloat() / c.target).coerceIn(0f, 1f)
            ChallengeType.SINGLE_SESSION -> 0f
        }
    }

    private fun buildLabel(c: Challenge, uc: UserChallenge, profile: UserProfile): String {
        return when (c.type) {
            ChallengeType.REPS_TOTAL -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                "$earned / ${c.target}"
            }
            ChallengeType.DAILY_REPS -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                "$earned / ${c.target * c.targetDays}"
            }
            ChallengeType.STREAK -> "${profile.currentStreak} / ${c.target} days"
            ChallengeType.SINGLE_SESSION -> "${c.target}+ in one session"
        }
    }

}
