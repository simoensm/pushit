package com.pushitup.core.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ── DataStore singleton ───────────────────────────────────────────────────────

val Context.notifPrefsStore: DataStore<Preferences> by preferencesDataStore("notification_prefs")

// ── Keys ──────────────────────────────────────────────────────────────────────

object NotificationPrefKeys {
    val DAILY_REMINDER_ENABLED   = booleanPreferencesKey("daily_reminder_enabled")
    val DAILY_REMINDER_HOUR      = intPreferencesKey("daily_reminder_hour")
    val DAILY_REMINDER_MINUTE    = intPreferencesKey("daily_reminder_minute")
    val LEADERBOARD_NOTIF        = booleanPreferencesKey("leaderboard_notif_enabled")
    val CHALLENGE_DEADLINE_NOTIF = booleanPreferencesKey("challenge_deadline_notif_enabled")
    // Persisted ranks for change detection
    val LAST_GLOBAL_RANK         = longPreferencesKey("last_global_rank")
    val LAST_DAILY_RANK          = longPreferencesKey("last_daily_rank")
}

// ── Data class ────────────────────────────────────────────────────────────────

data class NotificationPrefs(
    val dailyReminderEnabled  : Boolean = true,
    val dailyReminderHour     : Int     = 9,
    val dailyReminderMinute   : Int     = 0,
    val leaderboardNotif      : Boolean = true,
    val challengeDeadlineNotif: Boolean = true,
)

// ── Flow extension ────────────────────────────────────────────────────────────

fun DataStore<Preferences>.notifPrefsFlow(): Flow<NotificationPrefs> =
    data.map { prefs ->
        NotificationPrefs(
            dailyReminderEnabled   = prefs[NotificationPrefKeys.DAILY_REMINDER_ENABLED]   ?: true,
            dailyReminderHour      = prefs[NotificationPrefKeys.DAILY_REMINDER_HOUR]      ?: 9,
            dailyReminderMinute    = prefs[NotificationPrefKeys.DAILY_REMINDER_MINUTE]    ?: 0,
            leaderboardNotif       = prefs[NotificationPrefKeys.LEADERBOARD_NOTIF]        ?: true,
            challengeDeadlineNotif = prefs[NotificationPrefKeys.CHALLENGE_DEADLINE_NOTIF] ?: true,
        )
    }
