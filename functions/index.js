const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp }     = require("firebase-admin/app");
const { getFirestore }      = require("firebase-admin/firestore");
const { getMessaging }      = require("firebase-admin/messaging");

initializeApp();

/**
 * Fires whenever a new friendRequest document is created.
 * Looks up the recipient's FCM token and sends a push notification.
 *
 * Document shape expected:
 *   { fromUid, fromDisplayName, fromUsername, toUid, status: "pending", createdAt }
 */
exports.onFriendRequestCreated = onDocumentCreated(
  "friendRequests/{requestId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    const { fromUid, fromDisplayName, fromUsername, toUid, status } = data;

    // Only notify for new pending requests
    if (status !== "pending") return;

    // Look up the recipient's FCM token
    const recipientSnap = await getFirestore()
      .collection("users")
      .doc(toUid)
      .get();

    if (!recipientSnap.exists) return;

    const fcmToken = recipientSnap.data()?.fcmToken;
    if (!fcmToken) {
      console.log(`No FCM token for user ${toUid} — skipping notification`);
      return;
    }

    const senderName = fromDisplayName || fromUsername || "Someone";

    const message = {
      token: fcmToken,
      notification: {
        title: "New friend request 👋",
        body: `${senderName} wants to be your friend`,
      },
      data: {
        channel : "social",
        fromUid : fromUid,
        type    : "friend_request",
      },
      android: {
        priority: "high",
        notification: {
          channelId: "social",
          clickAction: "OPEN_FRIENDS",
        },
      },
    };

    try {
      const response = await getMessaging().send(message);
      console.log(`Friend request notification sent to ${toUid}: ${response}`);
    } catch (err) {
      console.error(`Failed to send notification to ${toUid}:`, err);
    }
  },
);

/**
 * Fires when a friend request is updated to "accepted".
 * Notifies the original sender that their request was accepted.
 */
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

exports.onFriendRequestAccepted = onDocumentUpdated(
  "friendRequests/{requestId}",
  async (event) => {
    const before = event.data?.before?.data();
    const after  = event.data?.after?.data();
    if (!before || !after) return;

    // Only act on the pending → accepted transition
    if (before.status !== "pending" || after.status !== "accepted") return;

    const { fromUid, toUid } = after;

    // Notify the sender that their request was accepted
    const senderSnap = await getFirestore()
      .collection("users")
      .doc(fromUid)
      .get();

    if (!senderSnap.exists) return;

    const fcmToken       = senderSnap.data()?.fcmToken;
    const acceptorSnap   = await getFirestore().collection("users").doc(toUid).get();
    const acceptorName   = acceptorSnap.data()?.displayName
                        || acceptorSnap.data()?.username
                        || "Someone";

    if (!fcmToken) return;

    const message = {
      token: fcmToken,
      notification: {
        title: "Friend request accepted! 🎉",
        body: `${acceptorName} accepted your friend request`,
      },
      data: {
        channel : "social",
        fromUid : toUid,
        type    : "friend_accepted",
      },
      android: {
        priority: "high",
        notification: { channelId: "social" },
      },
    };

    try {
      await getMessaging().send(message);
      console.log(`Acceptance notification sent to ${fromUid}`);
    } catch (err) {
      console.error(`Failed to send acceptance notification:`, err);
    }
  },
);
