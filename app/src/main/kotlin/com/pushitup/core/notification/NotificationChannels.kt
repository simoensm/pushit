package com.pushitup.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pushitup.MainActivity
import com.pushitup.R

// ── Channel IDs ───────────────────────────────────────────────────────────────

object NotificationChannels {
    const val DAILY_REMINDER = "daily_reminder"
    const val LEADERBOARD    = "leaderboard"
    const val CHALLENGES     = "challenges"
    const val SOCIAL         = "social"          // friend requests, acceptances
    const val APP_UPDATES    = "app_updates"     // new version available

    fun createAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    DAILY_REMINDER,
                    context.getString(R.string.notif_channel_reminder),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    LEADERBOARD,
                    context.getString(R.string.notif_channel_leaderboard),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHALLENGES,
                    context.getString(R.string.notif_channel_challenges),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    SOCIAL,
                    "Friends & Social",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Friend requests and social activity" },
                NotificationChannel(
                    APP_UPDATES,
                    "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Notified when a new version of PushIt is available" },
            )
        )
    }
}

// ── Notification IDs ──────────────────────────────────────────────────────────

object NotifIds {
    const val DAILY_REMINDER      = 1001
    const val LEADERBOARD_GLOBAL  = 1002
    const val LEADERBOARD_DAILY   = 1003
    const val CHALLENGE_BASE      = 2000  // + challengeId.hashCode()
    const val FRIEND_REQUEST_BASE = 3000  // + fromUid.hashCode()
    const val APP_UPDATE          = 4001
}

// ── Shared helper ─────────────────────────────────────────────────────────────

fun postNotification(
    ctx       : Context,
    channelId : String,
    notifId   : Int,
    title     : String,
    body      : String,
) {
    // Runtime permission check for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
    }

    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        ctx, notifId, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(ctx, channelId)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(
            if (channelId == NotificationChannels.DAILY_REMINDER)
                NotificationCompat.PRIORITY_HIGH
            else
                NotificationCompat.PRIORITY_DEFAULT
        )
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat.from(ctx).notify(notifId, notification)
}
