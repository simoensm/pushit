package com.pushitup.core.notification

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val wm: WorkManager get() = WorkManager.getInstance(ctx)

    // ── Daily reminder ─────────────────────────────────────────────────────────

    /**
     * Schedule (or re-schedule) the daily reminder.
     * Use [replace] = true only when the user explicitly changes the time — it resets the timer.
     * On app restore/reboot use [replace] = false (KEEP) so a pending alarm isn't pushed forward.
     */
    fun scheduleDailyReminder(hour: Int, minute: Int, replace: Boolean = true) {
        val delay = computeDelayUntil(hour, minute)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().build())
            .addTag(TAG_DAILY_REMINDER)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP
        wm.enqueueUniquePeriodicWork(TAG_DAILY_REMINDER, policy, request)
    }

    fun cancelDailyReminder() {
        wm.cancelUniqueWork(TAG_DAILY_REMINDER)
    }

    // ── Leaderboard check ──────────────────────────────────────────────────────

    fun scheduleLeaderboardCheck() {
        val request = PeriodicWorkRequestBuilder<LeaderboardCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_LEADERBOARD)
            .build()
        wm.enqueueUniquePeriodicWork(TAG_LEADERBOARD, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelLeaderboardCheck() {
        wm.cancelUniqueWork(TAG_LEADERBOARD)
    }

    // ── Challenge deadline check ───────────────────────────────────────────────

    fun scheduleChallengeDeadlineCheck() {
        val request = PeriodicWorkRequestBuilder<ChallengeDeadlineWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_CHALLENGES)
            .build()
        wm.enqueueUniquePeriodicWork(TAG_CHALLENGES, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelChallengeDeadlineCheck() {
        wm.cancelUniqueWork(TAG_CHALLENGES)
    }

    // ── App update check ───────────────────────────────────────────────────────

    /** Checks once per day if a new version is available on the Play Store. */
    fun scheduleUpdateCheck() {
        val request = PeriodicWorkRequestBuilder<com.pushitup.core.update.UpdateCheckWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_UPDATE_CHECK)
            .build()
        wm.enqueueUniquePeriodicWork(TAG_UPDATE_CHECK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /** Returns milliseconds until the next occurrence of hour:minute. */
    fun computeDelayUntil(hour: Int, minute: Int): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    companion object {
        const val TAG_DAILY_REMINDER = "daily_reminder_worker"
        const val TAG_LEADERBOARD    = "leaderboard_check_worker"
        const val TAG_CHALLENGES     = "challenge_deadline_worker"
        const val TAG_UPDATE_CHECK   = "update_check_worker"
    }
}
