package com.pushitup.domain.model

import java.time.Instant

data class WorkoutSession(
    val id: String,
    val uid: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val reps: Int,
    val durationSeconds: Long,
    /** Reps per minute, computed at session end. */
    val avgPace: Float,
    val isPersonalBest: Boolean,
)
