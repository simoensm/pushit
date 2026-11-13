package com.pushitup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Primary key is "{uid}_{date}" e.g. "abc123_2024-06-15". */
@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val date: String,          // ISO-8601 LocalDate: "2024-06-15"
    val totalReps: Int,
    val sessionIds: String,    // JSON array stored as text
    val syncedToFirestore: Boolean = false,
)
