package com.pushitup.domain.repository

import com.pushitup.domain.model.DailyLog
import com.pushitup.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WorkoutRepository {
    suspend fun saveSession(session: WorkoutSession)
    fun observeSessionsForUser(uid: String): Flow<List<WorkoutSession>>
    suspend fun getPersonalBestReps(uid: String): Int
    suspend fun getDailyLog(uid: String, date: LocalDate): DailyLog?
    fun observeDailyLog(uid: String, date: LocalDate): Flow<DailyLog?>
    fun observeWeeklyLogs(uid: String, from: LocalDate): Flow<List<DailyLog>>
    suspend fun syncPendingToFirestore(uid: String)
    /** Date of the user's most recent finished workout, or null if they've never worked out. */
    suspend fun getLastWorkoutDate(uid: String): LocalDate?
    /**
     * Fetches the last [days] daily logs from Firestore and seeds them into Room.
     * Call on login so streak calculation and the weekly chart work correctly
     * even when the user is on a fresh/new device.
     */
    suspend fun seedLocalLogsFromFirestore(uid: String, days: Int = 14)
}
