package com.pushitup.feature.workout.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.pushitup.domain.model.AllAchievements
import com.pushitup.domain.model.AllChallenges
import com.pushitup.domain.model.AchievementCondition
import com.pushitup.domain.model.ChallengeType
import com.pushitup.domain.model.PushUpVariant
import com.pushitup.domain.model.RankSystem
import com.pushitup.domain.model.WorkoutSession
import com.pushitup.domain.repository.UserRepository
import com.pushitup.domain.repository.WorkoutRepository
import com.pushitup.widget.FriendsRankingUpdateWorker
import com.pushitup.domain.usecase.XpCalculator
import com.pushitup.feature.workout.data.PhoneMotionDetector
import com.pushitup.feature.workout.data.PoseAnalyzer
import com.pushitup.feature.workout.domain.DetectionResult
import com.pushitup.feature.workout.domain.LandmarkPoint
import com.pushitup.feature.workout.domain.PushUpDetector
import com.pushitup.feature.workout.domain.PushUpState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class WorkoutRewardType { ACHIEVEMENT, CHALLENGE, RANK_UP }

data class WorkoutReward(
    val type     : WorkoutRewardType,
    val title    : String,
    val subtitle : String,
    val stars    : Long = 0L,
)

data class WorkoutUiState(
    val phase: WorkoutPhase = WorkoutPhase.IDLE,
    val selectedVariant: PushUpVariant = PushUpVariant.NORMAL,
    val repCount: Int = 0,
    val elapsedSeconds: Long = 0L,
    val pushUpState: PushUpState = PushUpState.IDLE,
    val badFormWarning: Boolean = false,
    val phoneMovingWarning: Boolean = false,
    /** True when a rep was rejected because wrists aren't in the right position for the selected variant. */
    val wrongVariantWarning: Boolean = false,
    val calibProgress: Float = 0f,
    val sessionSummary  : SessionSummary?      = null,
    val comboMultiplier : Int                  = 1,
    val consecutiveReps : Int                  = 0,
    /** Rewards earned this session — shown as celebration overlay before summary. */
    val pendingRewards  : List<WorkoutReward>  = emptyList(),
)

enum class WorkoutPhase { IDLE, ACTIVE, PAUSED, ENDED }

data class SessionSummary(
    val totalReps: Int,
    val durationSeconds: Long,
    val avgPace: Float,
    val isPersonalBest: Boolean,
    val xpEarned: Long,
    val starsEarned: Long = 0L,
    /** Extra stars earned above the 1-star-per-rep baseline thanks to combo multipliers. */
    val comboBonus: Long = 0L,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    val detector = PushUpDetector()

    private val vibrator = context.getSystemService(Vibrator::class.java)

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    private val _landmarks = MutableStateFlow<List<LandmarkPoint>?>(null)
    val landmarks: StateFlow<List<LandmarkPoint>?> = _landmarks.asStateFlow()

    // ── Phone motion detection ────────────────────────────────────────────────

    private val phoneMotionDetector = PhoneMotionDetector(context)

    // Tracks current phone-moving state so the pose callback can gate counting
    // without reading a StateFlow on every frame.
    @Volatile private var phoneIsMoving = false

    init {
        // Observe motion changes and mirror into UiState for the warning overlay.
        viewModelScope.launch {
            phoneMotionDetector.isMoving.collect { moving ->
                phoneIsMoving = moving
                _uiState.update { it.copy(phoneMovingWarning = moving) }
            }
        }
    }

    // ── ML Kit pose analyzer ──────────────────────────────────────────────────

    val poseAnalyzer: PoseAnalyzer = PoseAnalyzer(
        detector = detector,
        onResult = { detResult, lms ->
            _landmarks.value = lms          // always update skeleton display
            if (!phoneIsMoving) {           // only count reps when phone is still
                onPoseResult(detResult)
            }
        },
    )

    private var sessionStartInstant: Instant? = null
    private var timerJob: Job? = null

    // ── Combo-multiplier tracking ─────────────────────────────────────────────
    // Per-rep elapsed time is tracked with SystemClock.elapsedRealtime() so it
    // survives clock changes and is monotonic (no resets when the phone sleeps).

    /** Timestamp (elapsedRealtime ms) of the most recent rep in the current session. */
    private var lastRepTimeMs: Long = 0L

    /** Stars accumulated during this session via the combo multiplier. */
    private var sessionComboStars: Long = 0L

    // ── Session controls ──────────────────────────────────────────────────────

    fun selectVariant(variant: PushUpVariant) {
        if (_uiState.value.phase == WorkoutPhase.IDLE) {
            _uiState.update { it.copy(selectedVariant = variant) }
            detector.currentVariant = variant
        }
    }

    fun startSession() {
        if (_uiState.value.phase == WorkoutPhase.ACTIVE) return
        detector.reset()
        detector.currentVariant = _uiState.value.selectedVariant   // sync before counting starts
        sessionStartInstant = Instant.now()
        lastRepTimeMs       = 0L
        sessionComboStars   = 0L
        val variant = _uiState.value.selectedVariant
        _uiState.update { WorkoutUiState(phase = WorkoutPhase.ACTIVE, selectedVariant = variant) }
        phoneMotionDetector.start()
        startTimer()
    }

    fun pauseSession() {
        if (_uiState.value.phase != WorkoutPhase.ACTIVE) return
        timerJob?.cancel()
        phoneMotionDetector.stop()
        _uiState.update { it.copy(phase = WorkoutPhase.PAUSED, phoneMovingWarning = false) }
    }

    fun resumeSession() {
        if (_uiState.value.phase != WorkoutPhase.PAUSED) return
        _uiState.update { it.copy(phase = WorkoutPhase.ACTIVE) }
        phoneMotionDetector.start()
        startTimer()
    }

    fun endSession() {
        timerJob?.cancel()
        phoneMotionDetector.stop()
        val state = _uiState.value
        if (state.phase == WorkoutPhase.IDLE) return

        val uid      = auth.currentUser?.uid ?: "local_user"
        val reps     = state.repCount

        // Zero-rep session — nothing to save, just go back
        if (reps == 0) {
            _uiState.update { it.copy(phase = WorkoutPhase.IDLE) }
            viewModelScope.launch { _navigateBack.emit(Unit) }
            return
        }
        val duration = state.elapsedSeconds
        val avgPace  = if (duration > 0) reps / (duration / 60f) else 0f
        val xpEarned = XpCalculator.xpForSession(reps)

        _uiState.update {
            it.copy(
                phase = WorkoutPhase.ENDED,
                phoneMovingWarning = false,
                sessionSummary = SessionSummary(
                    totalReps       = reps,
                    durationSeconds = duration,
                    avgPace         = avgPace,
                    isPersonalBest  = false,
                    xpEarned        = xpEarned,
                    starsEarned     = 0L,
                ),
            )
        }

        viewModelScope.launch {
            try {
                val today     = LocalDate.now()
                val yesterday = today.minusDays(1)

                // Check yesterday's reps BEFORE saving today's session (to determine streak)
                val yesterdayLog = workoutRepository.getDailyLog(uid, yesterday)

                val prevBest       = workoutRepository.getPersonalBestReps(uid)
                val isPersonalBest = reps > prevBest
                val session = WorkoutSession(
                    id              = UUID.randomUUID().toString(),
                    uid             = uid,
                    startedAt       = sessionStartInstant ?: Instant.now(),
                    endedAt         = Instant.now(),
                    reps            = reps,
                    durationSeconds = duration,
                    avgPace         = avgPace,
                    isPersonalBest  = isPersonalBest,
                )
                workoutRepository.saveSession(session)
                workoutRepository.syncPendingToFirestore(uid)

                // Refresh the home-screen widget ranking with the new reps
                FriendsRankingUpdateWorker.enqueue(context)

                // ── Update user profile stars / totalReps / streak ──────────
                if (uid != "local_user") {
                    // Read profile only for streak logic (streaks need the previous state).
                    // totalReps and stars use FieldValue.increment to avoid read-then-write
                    // races when multiple sessions are saved quickly.
                    val profile    = userRepository.getProfile(uid).getOrNull()
                    val oldStreak  = profile?.currentStreak ?: 0
                    val oldLongest = profile?.longestStreak ?: 0

                    // Streak: only update on the first session of the day
                    val todayLog     = workoutRepository.getDailyLog(uid, today)
                    val isFirstToday = (todayLog?.sessionIds?.size ?: 0) <= 1
                    val newStreak = when {
                        reps == 0                                          -> oldStreak
                        !isFirstToday                                      -> oldStreak
                        yesterdayLog != null && yesterdayLog.totalReps > 0 -> oldStreak + 1
                        else                                               -> 1
                    }
                    val newLongest   = maxOf(oldLongest, newStreak)
                    val variantMulti = state.selectedVariant.starMultiplier
                    val starsEarned  = if (reps > 0) (RankSystem.starsForSession(reps, newStreak, sessionComboStars) * variantMulti).toLong() else 0L

                    Log.d(TAG, "Saving stats: reps=$reps starsEarned=$starsEarned streak=$newStreak")

                    val updateResult = userRepository.updateProfile(
                        uid,
                        mapOf(
                            // Atomic server-side increments — safe under concurrent session saves
                            "stars"         to FieldValue.increment(starsEarned),
                            "totalReps"     to FieldValue.increment(reps.toLong()),
                            // Streak is not incremental — we write the computed value directly
                            "currentStreak" to newStreak,
                            "longestStreak" to newLongest,
                        ),
                    )
                    if (updateResult.isFailure) {
                        Log.e(TAG, "Profile update failed", updateResult.exceptionOrNull())
                    }

                    // ── Detect newly unlocked achievements ──────────────────
                    val oldTotalReps   = profile?.totalReps ?: 0L
                    val newTotalReps   = oldTotalReps + reps
                    val oldLongestStr  = oldLongest
                    val claimedIds     = profile?.claimedAchievements ?: emptyList()
                    val newlyUnlocked  = AllAchievements.filter { ach ->
                        ach.id !in claimedIds &&
                        when (val c = ach.condition) {
                            is AchievementCondition.TotalReps     -> oldTotalReps < c.count && newTotalReps >= c.count
                            is AchievementCondition.Streak        -> oldLongest < c.days && newLongest >= c.days
                            is AchievementCondition.SingleSession -> reps.toLong() >= c.count && isPersonalBest
                            is AchievementCondition.FriendsCount  -> false // handled elsewhere
                        }
                    }

                    // ── Auto-claim newly unlocked achievements ──────────────
                    if (newlyUnlocked.isNotEmpty()) {
                        val newClaimed     = claimedIds + newlyUnlocked.map { it.id }
                        val achStars       = newlyUnlocked.sumOf { it.starReward }.toLong()
                        userRepository.updateProfile(
                            uid,
                            mapOf(
                                "claimedAchievements" to newClaimed,
                                "stars"               to FieldValue.increment(achStars),
                            ),
                        )
                        Log.d(TAG, "Auto-claimed ${newlyUnlocked.size} achievements +$achStars stars")
                    }

                    // ── Complete SINGLE_SESSION challenges ──────────────────
                    val activeChallenges = userRepository.getActiveChallenges(uid).getOrDefault(emptyList())
                    val completedChallenges = mutableListOf<com.pushitup.domain.model.Challenge>()
                    activeChallenges
                        .filter { uc -> !uc.completed }
                        .forEach { uc ->
                            val challenge = AllChallenges.find { it.id == uc.challengeId } ?: return@forEach
                            if (challenge.type == ChallengeType.SINGLE_SESSION && reps >= challenge.target) {
                                userRepository.completeChallenge(uid, challenge.id, challenge.starReward)
                                completedChallenges.add(challenge)
                                Log.d(TAG, "SINGLE_SESSION challenge completed: ${challenge.title} +${challenge.starReward} stars")
                            }
                        }

                    // ── Rank-up detection ───────────────────────────────────
                    val oldTier = RankSystem.tierFor(profile?.stars ?: 0L)
                    val newTier = RankSystem.tierFor((profile?.stars ?: 0L) + starsEarned)
                    val rankedUp = newTier != null && (oldTier == null || oldTier.baseName != newTier.baseName)

                    // ── Build rewards list for celebration overlay ──────────
                    val rewards = buildList<WorkoutReward> {
                        newlyUnlocked.forEach { ach ->
                            add(WorkoutReward(WorkoutRewardType.ACHIEVEMENT, ach.title, ach.description, ach.starReward.toLong()))
                        }
                        completedChallenges.forEach { ch ->
                            add(WorkoutReward(WorkoutRewardType.CHALLENGE, ch.title, ch.description, ch.starReward.toLong()))
                        }
                        if (rankedUp && newTier != null) {
                            add(WorkoutReward(WorkoutRewardType.RANK_UP, "Rank Up!", "You reached ${newTier.name}", 0L))
                        }
                    }

                    val comboBonus = maxOf(0L, sessionComboStars - reps)
                    _uiState.update { s ->
                        s.copy(
                            pendingRewards = rewards,
                            sessionSummary = s.sessionSummary?.copy(
                                isPersonalBest = isPersonalBest,
                                starsEarned    = starsEarned,
                                comboBonus     = comboBonus,
                            )
                        )
                    }
                } else {
                    _uiState.update { s ->
                        s.copy(sessionSummary = s.sessionSummary?.copy(isPersonalBest = isPersonalBest))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save session", e)
            }
        }
    }

    /** Called by the celebration overlay when the user taps "Continue". */
    fun dismissRewards() {
        _uiState.update { it.copy(pendingRewards = emptyList()) }
    }

    // ── Pose processing ───────────────────────────────────────────────────────

    fun onPoseResult(result: DetectionResult) {
        if (_uiState.value.phase != WorkoutPhase.ACTIVE) return

        if (result.repCounted) {
            hapticFeedback()

            val nowMs          = android.os.SystemClock.elapsedRealtime()
            val gapMs          = if (lastRepTimeMs == 0L) 0L else nowMs - lastRepTimeMs
            lastRepTimeMs      = nowMs

            val prevConsec     = if (gapMs in 1..<RankSystem.COMBO_WINDOW_MS) _uiState.value.consecutiveReps else 0
            val newConsec      = prevConsec + 1
            val multi          = RankSystem.comboMultiplier(newConsec)

            sessionComboStars += multi.toLong()

            _uiState.update {
                it.copy(
                    repCount             = result.repCount,
                    pushUpState          = result.state,
                    badFormWarning       = result.badFormDetected,
                    wrongVariantWarning  = false,
                    calibProgress        = result.calibProgress,
                    consecutiveReps      = newConsec,
                    comboMultiplier      = multi,
                )
            }
        } else if (result.wrongHandPosition) {
            // Rep was not counted — wrong hand position for the selected variant
            _uiState.update {
                it.copy(
                    pushUpState         = result.state,
                    badFormWarning      = result.badFormDetected,
                    wrongVariantWarning = true,
                    calibProgress       = result.calibProgress,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    pushUpState         = result.state,
                    badFormWarning      = result.badFormDetected,
                    wrongVariantWarning = false,
                    calibProgress       = result.calibProgress,
                )
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                if (_uiState.value.phase == WorkoutPhase.ACTIVE) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    private fun hapticFeedback() {
        vibrator?.vibrate(
            VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        poseAnalyzer.close()
        phoneMotionDetector.stop()
    }

    companion object {
        private const val TAG = "WorkoutViewModel"
    }
}
