package com.pushitup.core.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Runs every 6 hours. Checks the user's active (incomplete) challenges.
 * For each challenge that expires within the next 24 hours and isn't complete,
 * fires a single notification.
 */
class ChallengeDeadlineWorker(
    private val ctx   : Context,
    workerParams      : WorkerParameters,
) : CoroutineWorker(ctx, workerParams) {

    private val db by lazy { FirebaseFirestore.getInstance() }

    override suspend fun doWork(): Result {
        val prefs = ctx.notifPrefsStore.notifPrefsFlow().first()
        if (!prefs.challengeDeadlineNotif) return Result.success()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val nowMs      = System.currentTimeMillis()
        val in24hMs    = nowMs + 24L * 60 * 60 * 1000

        // Active (incomplete) user challenges
        val userChallenges = runCatching {
            db.collection("userChallenges")
                .whereEqualTo("uid", uid)
                .whereEqualTo("completed", false)
                .get()
                .await()
                .documents
        }.getOrDefault(emptyList())

        for (doc in userChallenges) {
            val challengeId = doc.getString("challengeId") ?: continue

            // Fetch the challenge definition for its end date
            val challengeDoc = runCatching {
                db.collection("challenges").document(challengeId).get().await()
            }.getOrNull() ?: continue

            val endDate = challengeDoc.getTimestamp("endDate")?.toDate()?.time ?: continue
            val title   = challengeDoc.getString("title") ?: continue

            if (endDate in (nowMs + 1)..in24hMs) {
                // Calculate hours left
                val hoursLeft = ((endDate - nowMs) / (1000 * 60 * 60)).toInt()
                val notifId = NotifIds.CHALLENGE_BASE + challengeId.hashCode()

                postNotification(
                    ctx       = ctx,
                    channelId = NotificationChannels.CHALLENGES,
                    notifId   = notifId,
                    title     = ctx.getString(com.pushitup.R.string.notif_challenge_deadline_title),
                    body      = ctx.getString(com.pushitup.R.string.notif_challenge_deadline_body, title, hoursLeft),
                )
            }
        }
        return Result.success()
    }
}
