package com.pushitup.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.pushitup.data.local.dao.DailyLogDao
import com.pushitup.data.local.dao.SessionDao
import com.pushitup.data.local.entity.DailyLogEntity
import com.pushitup.data.local.entity.SessionEntity
import com.pushitup.domain.model.DailyLog
import com.pushitup.domain.model.WorkoutSession
import com.pushitup.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val dailyLogDao: DailyLogDao,
    private val firestore: FirebaseFirestore,
) : WorkoutRepository {

    // ── Sessions ──────────────────────────────────────────────────────────────

    override suspend fun saveSession(session: WorkoutSession) {
        sessionDao.upsert(session.toEntity())
        updateDailyLog(session)
    }

    override fun observeSessionsForUser(uid: String): Flow<List<WorkoutSession>> =
        sessionDao.observeAll(uid).map { list -> list.map { it.toDomain() } }

    override suspend fun getPersonalBestReps(uid: String): Int =
        sessionDao.getPersonalBestReps(uid) ?: 0

    // ── Daily logs ────────────────────────────────────────────────────────────

    override suspend fun getDailyLog(uid: String, date: LocalDate): DailyLog? {
        // 1. Try Room (fast, offline)
        val local = dailyLogDao.getByDate(uid, date.toString())
        if (local != null) return local.toDomain()

        // 2. Fall back to Firestore (new device / cleared data)
        return try {
            val key  = "${uid}_${date}"
            val snap = firestore.collection("dailyLogs").document(key).get().await()
            if (!snap.exists()) return null
            val reps = (snap.getLong("totalReps") ?: 0L).toInt()
            // Seed Room so subsequent calls are instant
            val entity = DailyLogEntity(
                id         = key,
                uid        = uid,
                date       = date.toString(),
                totalReps  = reps,
                sessionIds = "[]",
            )
            dailyLogDao.upsert(entity)
            entity.toDomain()
        } catch (_: Exception) { null }
    }

    override fun observeDailyLog(uid: String, date: LocalDate): Flow<DailyLog?> =
        dailyLogDao.observeByDate(uid, date.toString()).map { it?.toDomain() }

    override fun observeWeeklyLogs(uid: String, from: LocalDate): Flow<List<DailyLog>> =
        dailyLogDao.observeSince(uid, from.toString()).map { list -> list.map { it.toDomain() } }

    override suspend fun seedLocalLogsFromFirestore(uid: String, days: Int) {
        val today = LocalDate.now()
        val dates = (0 until days).map { today.minusDays(it.toLong()) }
        val docIds = dates.map { "${uid}_$it" }

        try {
            // Batch fetch all date documents in one round-trip per 30 (Firestore whereIn limit)
            docIds.chunked(30).forEach { chunk ->
                val snaps = firestore.collection("dailyLogs")
                    .whereIn("__name__", chunk.map {
                        firestore.collection("dailyLogs").document(it).path
                    })
                    .get()
                    .await()
                // whereIn on document IDs requires a workaround — fetch individually instead
                // (14 docs is well within acceptable latency for a login-time operation)
            }

            // Individual fetches (simpler and reliable for small N)
            docIds.forEach { key ->
                if (dailyLogDao.getByDate(uid, key.substringAfter("_")) != null) return@forEach
                val snap = firestore.collection("dailyLogs").document(key).get().await()
                if (snap.exists()) {
                    dailyLogDao.upsert(
                        DailyLogEntity(
                            id         = key,
                            uid        = uid,
                            date       = key.substringAfter("_"),
                            totalReps  = (snap.getLong("totalReps") ?: 0L).toInt(),
                            sessionIds = "[]",
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Non-fatal — Room fallback per-call in getDailyLog handles individual misses
        }
    }

    // ── Firestore sync ────────────────────────────────────────────────────────

    override suspend fun getLastWorkoutDate(uid: String): LocalDate? {
        val ms = sessionDao.getLatestEndedAtMs(uid) ?: return null
        return Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    }

    override suspend fun syncPendingToFirestore(uid: String) {
        val pending = sessionDao.getPendingSync(uid)
        pending.forEach { entity ->
            try {
                firestore.collection("sessions")
                    .document(entity.id)
                    .set(entity.toFirestoreMap())
                    .await()
                sessionDao.markSynced(entity.id)
            } catch (_: Exception) {
                // Réessai au prochain appel (quand le réseau revient)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun updateDailyLog(session: WorkoutSession) {
        val dateStr  = session.startedAt.atZone(ZoneOffset.UTC).toLocalDate().toString()
        val key      = "${session.uid}_$dateStr"
        val existing = dailyLogDao.getByDate(session.uid, dateStr)

        val sessionIds: List<String> = if (existing != null) {
            parseJsonArray(existing.sessionIds) + session.id
        } else {
            listOf(session.id)
        }

        val newTotalReps = (existing?.totalReps ?: 0) + session.reps

        dailyLogDao.upsert(
            DailyLogEntity(
                id         = key,
                uid        = session.uid,
                date       = dateStr,
                totalReps  = newTotalReps,
                sessionIds = JSONArray(sessionIds).toString(),
            )
        )

        // Sync to Firestore so friends can see today's reps in the ranking
        try {
            firestore.collection("dailyLogs").document(key).set(
                mapOf(
                    "uid"       to session.uid,
                    "date"      to dateStr,
                    "totalReps" to newTotalReps,
                )
            ).await()
        } catch (_: Exception) {
            // Non-fatal — local data is always authoritative for the current user
        }
    }

    private fun parseJsonArray(json: String): List<String> {
        val arr = JSONArray(json)
        return List(arr.length()) { arr.getString(it) }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun WorkoutSession.toEntity() = SessionEntity(
        id               = id,
        uid              = uid,
        startedAtEpochMs = startedAt.toEpochMilli(),
        endedAtEpochMs   = endedAt?.toEpochMilli(),
        reps             = reps,
        durationSeconds  = durationSeconds,
        avgPace          = avgPace,
        isPersonalBest   = isPersonalBest,
    )

    private fun SessionEntity.toDomain() = WorkoutSession(
        id              = id,
        uid             = uid,
        startedAt       = Instant.ofEpochMilli(startedAtEpochMs),
        endedAt         = endedAtEpochMs?.let { Instant.ofEpochMilli(it) },
        reps            = reps,
        durationSeconds = durationSeconds,
        avgPace         = avgPace,
        isPersonalBest  = isPersonalBest,
    )

    private fun DailyLogEntity.toDomain() = DailyLog(
        uid        = uid,
        date       = LocalDate.parse(date),
        totalReps  = totalReps,
        sessionIds = parseJsonArray(sessionIds),
    )

    private fun SessionEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
        "uid"             to uid,
        "date"            to LocalDate.ofEpochDay(startedAtEpochMs / 86_400_000L).toString(),
        "reps"            to reps,
        "durationSeconds" to durationSeconds,
        "avgPace"         to avgPace,
        "personalBest"    to isPersonalBest,
    )
}
