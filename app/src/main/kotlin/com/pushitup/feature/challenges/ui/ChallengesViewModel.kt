package com.pushitup.feature.challenges.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pushitup.domain.model.*
import com.pushitup.domain.repository.AuthRepository
import com.pushitup.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengesUiState(
    val challenges       : List<ChallengeWithState> = emptyList(),
    val difficultyFilter : Difficulty?              = null,
    val isLoading        : Boolean                  = true,
    val toast            : String?                  = null,
    val userStars        : Long                     = 0L,
) {
    val filtered: List<ChallengeWithState>
        get() = if (difficultyFilter == null) challenges
                else challenges.filter { it.challenge.difficulty == difficultyFilter }
}

@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengesUiState())
    val uiState: StateFlow<ChallengesUiState> = _uiState.asStateFlow()

    init { setup() }

    // Tracks IDs already sent to completeChallenge this session — prevents
    // repeated Firestore writes on every Flow re-emission.
    private val completionTriggered = mutableSetOf<String>()

    private fun setup() {
        val uid = authRepo.currentUid ?: return

        combine(
            userRepo.profileFlow(uid),
            userRepo.activeChallengesFlow(uid),
        ) { profile, activeList ->
            val byId = activeList.associateBy { it.challengeId }

            val challenges = AllChallenges.map { c ->
                val uc      = byId[c.id]
                val expired = uc != null && !uc.completed &&
                    System.currentTimeMillis() - uc.startedAt > c.durationDays * 86_400_000L
                ChallengeWithState(
                    challenge     = c,
                    userChallenge = uc,
                    progress      = computeProgress(c, uc, profile),
                    progressLabel = buildProgressLabel(c, uc, profile),
                    isExpired     = expired,
                )
            }
            Pair(challenges, profile?.stars ?: 0L)
        }
        .onEach { (challenges, stars) ->
            _uiState.update { it.copy(challenges = challenges, userStars = stars, isLoading = false) }

            // Auto-complete challenges that just reached 100 %.
            // SINGLE_SESSION is handled at session-end in WorkoutViewModel (needs rep count).
            challenges
                .filter { cws ->
                    val uc = cws.userChallenge
                    uc != null &&
                    !uc.completed &&
                    cws.progress >= 1f &&
                    cws.challenge.type != ChallengeType.SINGLE_SESSION &&
                    cws.challenge.id !in completionTriggered
                }
                .forEach { cws ->
                    completionTriggered += cws.challenge.id
                    viewModelScope.launch {
                        val result = userRepo.completeChallenge(uid, cws.challenge.id, cws.challenge.starReward)
                        if (result.isSuccess) {
                            _uiState.update {
                                it.copy(toast = "🏆 ${cws.challenge.title} completed! +${cws.challenge.starReward} stars")
                            }
                        }
                    }
                }
        }
        .launchIn(viewModelScope)
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    fun setFilter(difficulty: Difficulty?) {
        _uiState.update { it.copy(difficultyFilter = difficulty) }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun activateChallenge(challenge: Challenge) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            val profile = userRepo.getProfile(uid).getOrNull() ?: return@launch
            // Affordability guard
            if (challenge.starCost > 0 && profile.stars < challenge.starCost) {
                _uiState.update { it.copy(toast = "Not enough stars — need ${challenge.starCost} ★ to enter") }
                return@launch
            }
            val uc = UserChallenge(
                uid         = uid,
                challengeId = challenge.id,
                startedAt   = System.currentTimeMillis(),
                baseReps    = profile.totalReps,
                baseStreak  = profile.currentStreak,
            )
            val result = userRepo.activateChallenge(uc)
            if (result.isSuccess && challenge.starCost > 0) {
                // Deduct entry fee atomically
                userRepo.updateProfile(
                    uid,
                    mapOf("stars" to com.google.firebase.firestore.FieldValue.increment(-challenge.starCost.toLong())),
                )
            }
            _uiState.update {
                it.copy(
                    toast = when {
                        result.isFailure    -> "Could not activate challenge"
                        challenge.starCost > 0 ->
                            "−${challenge.starCost} ★ entry paid. Win +${challenge.starReward} ★ on completion!"
                        else                -> "${challenge.title} activated! +${challenge.starReward} ★ on completion"
                    }
                )
            }
        }
    }

    fun abandonChallenge(challenge: Challenge) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            userRepo.abandonChallenge(uid, challenge.id)
            _uiState.update { it.copy(toast = "${challenge.title} abandoned") }
        }
    }

    fun clearToast() = _uiState.update { it.copy(toast = null) }

    // ── Progress computation ──────────────────────────────────────────────────

    private fun computeProgress(c: Challenge, uc: UserChallenge?, profile: UserProfile?): Float {
        if (uc == null || profile == null) return 0f
        if (uc.completed) return 1f
        return when (c.type) {
            ChallengeType.REPS_TOTAL -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                (earned.toFloat() / c.target).coerceIn(0f, 1f)
            }
            ChallengeType.DAILY_REPS -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                val total  = (c.target * c.targetDays).toLong()
                (earned.toFloat() / total).coerceIn(0f, 1f)
            }
            ChallengeType.STREAK -> {
                (profile.currentStreak.toFloat() / c.target).coerceIn(0f, 1f)
            }
            ChallengeType.SINGLE_SESSION -> 0f
        }
    }

    private fun buildProgressLabel(c: Challenge, uc: UserChallenge?, profile: UserProfile?): String {
        if (uc == null || profile == null) return ""
        if (uc.completed) return "Completed!"
        return when (c.type) {
            ChallengeType.REPS_TOTAL -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                "$earned / ${c.target} reps"
            }
            ChallengeType.DAILY_REPS -> {
                val earned = (profile.totalReps - uc.baseReps).coerceAtLeast(0)
                val total  = c.target * c.targetDays
                "$earned / $total reps (${c.target}/day x ${c.targetDays} days)"
            }
            ChallengeType.STREAK -> "${profile.currentStreak} / ${c.target} day streak"
            ChallengeType.SINGLE_SESSION -> "Do a session with ${c.target}+ reps"
        }
    }
}
