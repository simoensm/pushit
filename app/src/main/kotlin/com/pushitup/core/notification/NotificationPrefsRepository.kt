package com.pushitup.core.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPrefsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    val prefsFlow: Flow<NotificationPrefs> = ctx.notifPrefsStore.notifPrefsFlow()

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        ctx.notifPrefsStore.edit { it[NotificationPrefKeys.DAILY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setDailyReminderTime(hour: Int, minute: Int) {
        ctx.notifPrefsStore.edit {
            it[NotificationPrefKeys.DAILY_REMINDER_HOUR]   = hour
            it[NotificationPrefKeys.DAILY_REMINDER_MINUTE] = minute
        }
    }

    suspend fun setLeaderboardNotif(enabled: Boolean) {
        ctx.notifPrefsStore.edit { it[NotificationPrefKeys.LEADERBOARD_NOTIF] = enabled }
    }

    suspend fun setChallengeDeadlineNotif(enabled: Boolean) {
        ctx.notifPrefsStore.edit { it[NotificationPrefKeys.CHALLENGE_DEADLINE_NOTIF] = enabled }
    }

    /** Persisted rank helpers used by workers (no Hilt injection needed in workers). */
    suspend fun saveLastGlobalRank(rank: Long) {
        ctx.notifPrefsStore.edit { it[NotificationPrefKeys.LAST_GLOBAL_RANK] = rank }
    }

    suspend fun saveLastDailyRank(rank: Long) {
        ctx.notifPrefsStore.edit { it[NotificationPrefKeys.LAST_DAILY_RANK] = rank }
    }
}
