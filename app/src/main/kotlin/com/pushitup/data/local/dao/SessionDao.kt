package com.pushitup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pushitup.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE uid = :uid ORDER BY startedAtEpochMs DESC")
    fun observeAll(uid: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE uid = :uid AND syncedToFirestore = 0")
    suspend fun getPendingSync(uid: String): List<SessionEntity>

    @Query("UPDATE sessions SET syncedToFirestore = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("""
        SELECT MAX(reps) FROM sessions
        WHERE uid = :uid AND endedAtEpochMs IS NOT NULL
    """)
    suspend fun getPersonalBestReps(uid: String): Int?

    /** Epoch-ms of the most recent finished session for this user. Null if none. */
    @Query("""
        SELECT MAX(endedAtEpochMs) FROM sessions
        WHERE uid = :uid AND endedAtEpochMs IS NOT NULL
    """)
    suspend fun getLatestEndedAtMs(uid: String): Long?
}
