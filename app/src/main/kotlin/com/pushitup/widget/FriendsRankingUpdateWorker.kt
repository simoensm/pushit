package com.pushitup.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that fetches today's friends ranking from Firestore,
 * serialises the top 5 entries to SharedPreferences, and triggers a widget
 * re-render via Glance.
 *
 * Runs immediately on first enqueue and then repeats every 30 minutes.
 */
class FriendsRankingUpdateWorker(
    context: Context,
    params : WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid  = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db   = FirebaseFirestore.getInstance()
        val users = db.collection("users")
        val today = LocalDate.now().toString()

        return try {
            // 1. Load current user's profile (contains friends list)
            val mySnap   = users.document(uid).get().await()
            val friendUids = (mySnap.get("friends") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            val allUids = (friendUids + uid).distinct()

            // 2. Fetch profiles
            val profiles = if (allUids.size == 1) {
                listOf(mySnap)
            } else {
                allUids.chunked(30).flatMap { chunk ->
                    users.whereIn("uid", chunk).get().await().documents
                }
            }

            // 3. Fetch today's reps for each uid
            val dailyLogs = db.collection("dailyLogs")
            val repsByUid = mutableMapOf<String, Int>()
            allUids.forEach { id ->
                try {
                    val logSnap = dailyLogs.document("${id}_$today").get().await()
                    if (logSnap.exists()) {
                        repsByUid[id] = (logSnap.getLong("totalReps") ?: 0L).toInt()
                    }
                } catch (_: Exception) { /* keep 0 */ }
            }

            // 4. Build + sort ranking (top 5 for widget)
            val entries = profiles.mapNotNull { doc ->
                val docUid   = doc.getString("uid") ?: return@mapNotNull null
                val name     = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                    ?: doc.getString("username") ?: "?"
                val streak   = (doc.getLong("currentStreak") ?: 0L).toInt()
                val todayReps = repsByUid[docUid] ?: 0
                JSONObject().apply {
                    put("uid",       docUid)
                    put("name",      name)
                    put("todayReps", todayReps)
                    put("streak",    streak)
                    put("isMe",      docUid == uid)
                }
            }.sortedByDescending { it.getInt("todayReps") }.take(5)

            // 5. Persist to SharedPrefs (widget reads from here)
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_RANKING_JSON, JSONArray(entries).toString())
                .apply()

            // 6. Trigger widget re-render
            FriendsRankingWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            // Retry up to 3 times on network failure
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PREFS_NAME        = "pushit_widget_prefs"
        const val KEY_RANKING_JSON  = "ranking_json"
        private const val WORK_TAG  = "friends_ranking_widget"

        /**
         * Enqueue an immediate run + periodic 30-min refresh.
         * Safe to call multiple times — existing work is kept.
         */
        fun enqueue(context: Context) {
            val workManager = WorkManager.getInstance(context)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // One-shot immediate fetch
            val oneShot = OneTimeWorkRequestBuilder<FriendsRankingUpdateWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()
            workManager.enqueueUniqueWork(
                "${WORK_TAG}_once",
                ExistingWorkPolicy.REPLACE,
                oneShot,
            )

            // Periodic every 30 minutes (minimum enforced by WorkManager is 15 min)
            val periodic = PeriodicWorkRequestBuilder<FriendsRankingUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "${WORK_TAG}_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
        }

        /** Cancel all widget refresh work (called when all widget instances are removed). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
