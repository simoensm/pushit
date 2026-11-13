package com.pushitup.core.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushitup.BuildConfig
import com.pushitup.core.notification.NotificationChannels
import kotlinx.coroutines.flow.first
import com.pushitup.core.notification.NotifIds
import com.pushitup.core.notification.postNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs once per day (when network is available).
 * Fetches the latest version from Firestore and posts a local notification
 * if a newer version is available and we haven't already notified for it.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val updateRepo: AppUpdateRepository,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val remote = updateRepo.fetchLatestVersion().getOrNull() ?: return Result.retry()

        if (remote.latestVersionCode <= BuildConfig.VERSION_CODE) return Result.success()

        // Check if we already notified for this exact version
        val store = ctx.updateStore
        val lastNotified = store.lastKnownLatestVersionFlow().first()
        if (lastNotified >= remote.latestVersionCode) return Result.success()

        // Save so we don't spam
        store.saveLatestVersion(remote.latestVersionCode)

        postNotification(
            ctx       = ctx,
            channelId = NotificationChannels.APP_UPDATES,
            notifId   = NotifIds.APP_UPDATE,
            title     = "PushIt ${remote.latestVersionName} is available!",
            body      = "A new version is ready on the Play Store. Tap to update and see what's new.",
        )

        return Result.success()
    }
}
