package com.pushitup.core.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Runs once per day. Posts a push-up reminder only if the user hasn't already
 * logged reps today and if the daily reminder preference is enabled.
 */
class DailyReminderWorker(
    private val ctx   : Context,
    workerParams      : WorkerParameters,
) : CoroutineWorker(ctx, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = ctx.notifPrefsStore.notifPrefsFlow().first()
        if (!prefs.dailyReminderEnabled) return Result.success()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        // Check today's reps from Firestore
        val today = java.time.LocalDate.now().toString()
        val todayReps = runCatching {
            FirebaseFirestore.getInstance()
                .collection("dailyLogs")
                .document("${uid}_${today}")
                .get()
                .await()
                .getLong("totalReps") ?: 0L
        }.getOrDefault(0L)

        // Only remind if user hasn't worked out at all today
        if (todayReps > 0) return Result.success()

        // Fetch streak for a personalized message
        val streak = runCatching {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .await()
                .getLong("currentStreak") ?: 0L
        }.getOrDefault(0L)

        val title = ctx.getString(com.pushitup.R.string.notif_reminder_title)
        val body = if (streak > 0) {
            ctx.getString(com.pushitup.R.string.notif_reminder_body_streak, streak)
        } else {
            ctx.getString(com.pushitup.R.string.notif_reminder_body)
        }

        postNotification(
            ctx       = ctx,
            channelId = NotificationChannels.DAILY_REMINDER,
            notifId   = NotifIds.DAILY_REMINDER,
            title     = title,
            body      = body,
        )
        return Result.success()
    }
}
