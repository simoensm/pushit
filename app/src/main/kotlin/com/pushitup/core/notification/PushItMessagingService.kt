package com.pushitup.core.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pushitup.R

/**
 * Handles FCM push messages (server-sent challenges, friend activity, etc.).
 * Channels are already created by [NotificationChannels.createAll] in Application.onCreate,
 * so we just post using the correct channel IDs.
 */
class PushItMessagingService : FirebaseMessagingService() {

    /**
     * Called when FCM assigns a new registration token (first install, or token rotation).
     * We save it to Firestore so Cloud Functions can look it up when sending push notifications.
     */
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
            // ignore failure — token will be re-saved on next app start via MainActivity
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title     = message.notification?.title ?: message.data["title"] ?: return
        val body      = message.notification?.body  ?: message.data["body"]  ?: return
        val channelId = message.data["channel"] ?: NotificationChannels.SOCIAL
        val notifId   = when (channelId) {
            NotificationChannels.SOCIAL ->
                NotifIds.FRIEND_REQUEST_BASE + (message.data["fromUid"]?.hashCode() ?: 0)
            else -> System.currentTimeMillis().toInt()
        }

        postNotification(
            ctx       = applicationContext,
            channelId = channelId,
            notifId   = notifId,
            title     = title,
            body      = body,
        )
    }
}
