package com.pushitup

import android.app.Application
import com.pushitup.core.notification.NotificationChannels
import com.pushitup.core.notification.NotificationPrefsRepository
import com.pushitup.core.notification.NotificationScheduler
import com.pushitup.core.notification.notifPrefsFlow
import com.pushitup.core.notification.notifPrefsStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PushItApplication : Application() {

    @Inject lateinit var scheduler: NotificationScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        restoreScheduledWorkers()
    }

    /**
     * Re-enqueue WorkManager jobs on every app start so they survive device
     * reboots and app updates. Uses KEEP policy so existing workers are not
     * reset unnecessarily.
     */
    private fun restoreScheduledWorkers() {
        appScope.launch {
            val prefs = notifPrefsStore.notifPrefsFlow().first()

            if (prefs.dailyReminderEnabled) {
                // replace=false: don't reset an already-pending alarm on every app open
                scheduler.scheduleDailyReminder(prefs.dailyReminderHour, prefs.dailyReminderMinute, replace = false)
            }
            if (prefs.leaderboardNotif) {
                scheduler.scheduleLeaderboardCheck()
            }
            if (prefs.challengeDeadlineNotif) {
                scheduler.scheduleChallengeDeadlineCheck()
            }
            // Always keep the update-check worker running (no user toggle needed)
            scheduler.scheduleUpdateCheck()
        }
    }
}
