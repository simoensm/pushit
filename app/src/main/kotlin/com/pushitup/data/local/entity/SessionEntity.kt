package com.pushitup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val reps: Int,
    val durationSeconds: Long,
    val avgPace: Float,
    val isPersonalBest: Boolean,
    val syncedToFirestore: Boolean = false,
)
