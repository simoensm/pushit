package com.pushitup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pushitup.data.local.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DailyLogEntity)

    @Query("SELECT * FROM daily_logs WHERE uid = :uid AND date = :date LIMIT 1")
    suspend fun getByDate(uid: String, date: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE uid = :uid AND date = :date LIMIT 1")
    fun observeByDate(uid: String, date: String): Flow<DailyLogEntity?>

    /** Last 7 days in descending order. */
    @Query("""
        SELECT * FROM daily_logs
        WHERE uid = :uid AND date >= :fromDate
        ORDER BY date DESC
    """)
    fun observeSince(uid: String, fromDate: String): Flow<List<DailyLogEntity>>

    @Query("SELECT COUNT(*) FROM daily_logs WHERE uid = :uid AND totalReps > 0 AND date >= :fromDate")
    suspend fun countActiveDaysSince(uid: String, fromDate: String): Int
}
