package com.pushitup.core.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Runs every hour. Detects rank improvements on the global leaderboard (all-time reps)
 * and the daily leaderboard among friends, then fires a notification when the user
 * moves up in rank.
 */
class LeaderboardCheckWorker(
    private val ctx   : Context,
    workerParams      : WorkerParameters,
) : CoroutineWorker(ctx, workerParams) {

    private val db by lazy { FirebaseFirestore.getInstance() }

    override suspend fun doWork(): Result {
        val prefs = ctx.notifPrefsStore.notifPrefsFlow().first()
        if (!prefs.leaderboardNotif) return Result.success()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val storedPrefs    = ctx.notifPrefsStore.data.first()
        val lastGlobalRank = storedPrefs[NotificationPrefKeys.LAST_GLOBAL_RANK] ?: Long.MAX_VALUE
        val lastDailyRank  = storedPrefs[NotificationPrefKeys.LAST_DAILY_RANK]  ?: Long.MAX_VALUE

        checkGlobalRank(uid, lastGlobalRank)
        checkDailyRank(uid, lastDailyRank)
        return Result.success()
    }

    private suspend fun checkGlobalRank(uid: String, lastRank: Long) {
        // Bail out entirely on any Firestore error — never compute a rank from partial data
        val myTotalReps = runCatching {
            db.collection("users").document(uid).get().await().getLong("totalReps") ?: 0L
        }.getOrNull() ?: return

        val usersAhead = runCatching {
            db.collection("users")
                .whereGreaterThan("totalReps", myTotalReps)
                .get()
                .await()
                .size()
                .toLong()
        }.getOrNull() ?: return   // ← key fix: return instead of defaulting to MAX_VALUE

        val currentRank = usersAhead + 1

        // Notify only on genuine improvement; skip the very first check (lastRank == MAX_VALUE)
        if (lastRank != Long.MAX_VALUE && currentRank < lastRank) {
            postNotification(
                ctx       = ctx,
                channelId = NotificationChannels.LEADERBOARD,
                notifId   = NotifIds.LEADERBOARD_GLOBAL,
                title     = ctx.getString(com.pushitup.R.string.notif_leaderboard_global_title),
                body      = ctx.getString(com.pushitup.R.string.notif_leaderboard_global_body, currentRank.toInt()),
            )
        }

        ctx.notifPrefsStore.edit { it[NotificationPrefKeys.LAST_GLOBAL_RANK] = currentRank }
    }

    private suspend fun checkDailyRank(uid: String, lastRank: Long) {
        val today = java.time.LocalDate.now().toString()

        val friends = runCatching {
            @Suppress("UNCHECKED_CAST")
            db.collection("users").document(uid).get().await()
                .get("friends") as? List<String> ?: emptyList()
        }.getOrNull() ?: return

        if (friends.isEmpty()) return

        val myDailyReps = runCatching {
            db.collection("dailyLogs").document("${uid}_$today").get().await()
                .getLong("totalReps") ?: 0L
        }.getOrNull() ?: return

        // Count friends with strictly more daily reps than the current user
        var friendsAhead = 0L
        for (friendUid in friends) {
            val friendReps = runCatching {
                db.collection("dailyLogs").document("${friendUid}_$today").get().await()
                    .getLong("totalReps") ?: 0L
            }.getOrNull() ?: continue   // skip this friend on error, don't abort the whole check
            if (friendReps > myDailyReps) friendsAhead++
        }

        val currentRank = friendsAhead + 1

        if (lastRank != Long.MAX_VALUE && currentRank < lastRank) {
            postNotification(
                ctx       = ctx,
                channelId = NotificationChannels.LEADERBOARD,
                notifId   = NotifIds.LEADERBOARD_DAILY,
                title     = ctx.getString(com.pushitup.R.string.notif_leaderboard_daily_title),
                body      = ctx.getString(com.pushitup.R.string.notif_leaderboard_daily_body, currentRank.toInt()),
            )
        }

        ctx.notifPrefsStore.edit { it[NotificationPrefKeys.LAST_DAILY_RANK] = currentRank }
    }
}
