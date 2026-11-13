package com.pushitup.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import com.google.firebase.storage.FirebaseStorage
import com.pushitup.domain.model.FriendRankEntry
import com.pushitup.domain.model.FriendRequest
import com.pushitup.domain.model.RankSystem
import com.pushitup.domain.model.UserChallenge
import com.pushitup.domain.model.UserProfile
import com.pushitup.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Usernames that are hidden from leaderboards, search, and all public listings. */
private val ADMIN_USERNAMES = setOf("admin")

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val db     : FirebaseFirestore,
    private val storage: FirebaseStorage,
) : UserRepository {

    private val users          = db.collection("users")
    private val requests       = db.collection("friendRequests")
    private val userChallenges = db.collection("userChallenges")

    // ── Profile ───────────────────────────────────────────────────────────────

    override suspend fun createProfile(profile: UserProfile): Result<Unit> = runCatching {
        users.document(profile.uid).set(profile.toMap()).await()
    }

    override suspend fun getProfile(uid: String): Result<UserProfile> = runCatching {
        val snap = users.document(uid).get().await()
        snap.toUserProfile() ?: error("Profile not found for $uid")
    }

    override fun profileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.toUserProfile())
        }
        awaitClose { reg.remove() }
    }

    override suspend fun updateProfile(uid: String, updates: Map<String, Any>): Result<Unit> =
        // Use set+merge instead of update so this is safe even if the document was never created
        // (e.g. account created before profile write was guaranteed).
        runCatching {
            users.document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .await()
        }

    override suspend fun isUsernameTaken(username: String, excludeUid: String?): Boolean {
        val snap = users.whereEqualTo("username", username.lowercase().trim()).limit(2).get().await()
        if (snap.isEmpty) return false
        if (excludeUid == null) return true
        // Allow the current user to keep their own username
        return snap.documents.any { doc -> doc.getString("uid") != excludeUid }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    override suspend fun searchByUsername(query: String): Result<List<UserProfile>> = runCatching {
        // Firestore range query simulates prefix search: "nick" → ["nick", "nicl")
        val end = query.dropLast(1) + (query.last() + 1)
        users
            .whereGreaterThanOrEqualTo("username", query)
            .whereLessThan("username", end)
            .limit(20)
            .get()
            .await()
            .mapNotNull { it.toUserProfile() }
            .filter { !ADMIN_USERNAMES.contains(it.username) }  // hide admin accounts
    }

    override suspend fun uploadPhoto(uid: String, imageBytes: ByteArray): Result<String> = runCatching {
        val ref = storage.reference.child("photos/$uid.jpg")
        ref.putBytes(imageBytes).await()
        val url = ref.downloadUrl.await().toString()
        // Save URL + cache-bust timestamp so Coil re-fetches even if the path didn't change
        val now = System.currentTimeMillis()
        users.document(uid)
            .set(mapOf("photoUrl" to url, "photoUpdatedAt" to now), com.google.firebase.firestore.SetOptions.merge())
            .await()
        url
    }

    override suspend fun clearPhoto(uid: String): Result<Unit> = runCatching {
        users.document(uid)
            .update(
                mapOf(
                    "photoUrl"       to com.google.firebase.firestore.FieldValue.delete(),
                    "photoUpdatedAt" to 0L,
                )
            )
            .await()
    }

    override suspend fun getTodayReps(uid: String): Int {
        val today = java.time.LocalDate.now().toString()
        return try {
            val snap = db.collection("dailyLogs").document("${uid}_$today").get().await()
            if (snap.exists()) (snap.getLong("totalReps") ?: 0L).toInt() else 0
        } catch (_: Exception) { 0 }
    }

    // ── Friend requests ───────────────────────────────────────────────────────

    override suspend fun sendFriendRequest(fromUid: String, toUid: String): Result<Unit> =
        runCatching {
            val fromProfile = users.document(fromUid).get().await().toUserProfile()
                ?: error("Profile not found")

            // Already friends?
            if (toUid in fromProfile.friends) error("Already friends")

            // Pending request in either direction already exists?
            val outgoing = requests
                .whereEqualTo("fromUid", fromUid)
                .whereEqualTo("toUid", toUid)
                .whereEqualTo("status", "pending")
                .get().await()
            if (!outgoing.isEmpty) error("Friend request already sent")

            val incoming = requests
                .whereEqualTo("fromUid", toUid)
                .whereEqualTo("toUid", fromUid)
                .whereEqualTo("status", "pending")
                .get().await()
            if (!incoming.isEmpty) error("This person already sent you a request")

            requests.add(
                mapOf(
                    "fromUid"         to fromUid,
                    "fromUsername"    to fromProfile.username,
                    "fromDisplayName" to fromProfile.displayName,
                    "fromPhotoUrl"    to fromProfile.photoUrl,
                    "toUid"           to toUid,
                    "status"          to "pending",
                    "createdAt"       to System.currentTimeMillis(),
                )
            ).await()
        }

    override suspend fun acceptFriendRequest(
        requestId: String,
        myUid: String,
        otherUid: String,
    ): Result<Unit> = runCatching {
        val batch = db.batch()
        // Update request status
        batch.update(requests.document(requestId), "status", "accepted")
        // Add each uid to the other's friends array
        batch.update(users.document(myUid),    "friends", FieldValue.arrayUnion(otherUid))
        batch.update(users.document(otherUid), "friends", FieldValue.arrayUnion(myUid))
        batch.commit().await()
    }

    override suspend fun declineFriendRequest(requestId: String): Result<Unit> = runCatching {
        requests.document(requestId).update("status", "declined").await()
    }

    override fun incomingRequestsFlow(uid: String): Flow<List<FriendRequest>> = callbackFlow {
        val reg = requests
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val all = snap?.documents?.map { doc ->
                    FriendRequest(
                        id              = doc.id,
                        fromUid         = doc.getString("fromUid") ?: "",
                        fromUsername    = doc.getString("fromUsername") ?: "",
                        fromDisplayName = doc.getString("fromDisplayName") ?: "",
                        fromPhotoUrl    = doc.getString("fromPhotoUrl"),
                        toUid           = doc.getString("toUid") ?: "",
                        status          = doc.getString("status") ?: "pending",
                        createdAt       = doc.getLong("createdAt") ?: 0L,
                    )
                } ?: emptyList()
                // Keep only the latest request per sender to de-duplicate spammed requests
                val deduplicated = all.distinctBy { it.fromUid }
                trySend(deduplicated)
            }
        awaitClose { reg.remove() }
    }

    override fun outgoingRequestsFlow(uid: String): Flow<List<FriendRequest>> = callbackFlow {
        val reg = requests
            .whereEqualTo("fromUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.map { doc ->
                    FriendRequest(
                        id              = doc.id,
                        fromUid         = doc.getString("fromUid") ?: "",
                        fromUsername    = doc.getString("fromUsername") ?: "",
                        fromDisplayName = doc.getString("fromDisplayName") ?: "",
                        fromPhotoUrl    = doc.getString("fromPhotoUrl"),
                        toUid           = doc.getString("toUid") ?: "",
                        status          = doc.getString("status") ?: "pending",
                        createdAt       = doc.getLong("createdAt") ?: 0L,
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    override fun friendsFlow(uid: String): Flow<List<UserProfile>> = callbackFlow {
        // Listen to our own profile to get the friends list, then fetch each friend
        val reg = users.document(uid).addSnapshotListener { snap, _ ->
            val friendUids = snap?.get("friends") as? List<*> ?: emptyList<Any>()
            if (friendUids.isEmpty()) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            // Fetch all friends in one query (Firestore whereIn supports up to 30)
            users.whereIn("uid", friendUids.take(30))
                .get()
                .addOnSuccessListener { friendSnap ->
                    trySend(friendSnap.mapNotNull { it.toUserProfile() })
                }
        }
        awaitClose { reg.remove() }
    }

    override suspend fun removeFriend(myUid: String, otherUid: String): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(users.document(myUid),    "friends", FieldValue.arrayRemove(otherUid))
        batch.update(users.document(otherUid), "friends", FieldValue.arrayRemove(myUid))
        batch.commit().await()
    }

    // ── Challenges ────────────────────────────────────────────────────────────

    override fun activeChallengesFlow(uid: String): Flow<List<UserChallenge>> = callbackFlow {
        val reg = userChallenges
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { doc ->
                    UserChallenge(
                        uid         = doc.getString("uid")         ?: return@mapNotNull null,
                        challengeId = doc.getString("challengeId") ?: return@mapNotNull null,
                        startedAt   = doc.getLong("startedAt")     ?: 0L,
                        baseReps    = doc.getLong("baseReps")       ?: 0L,
                        baseStreak  = doc.getLong("baseStreak")?.toInt() ?: 0,
                        completed   = doc.getBoolean("completed")   ?: false,
                        completedAt = doc.getLong("completedAt")    ?: 0L,
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun getActiveChallenges(uid: String): Result<List<UserChallenge>> = runCatching {
        userChallenges
            .whereEqualTo("uid", uid)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                UserChallenge(
                    uid         = doc.getString("uid")         ?: return@mapNotNull null,
                    challengeId = doc.getString("challengeId") ?: return@mapNotNull null,
                    startedAt   = doc.getLong("startedAt")     ?: 0L,
                    baseReps    = doc.getLong("baseReps")       ?: 0L,
                    baseStreak  = doc.getLong("baseStreak")?.toInt() ?: 0,
                    completed   = doc.getBoolean("completed")   ?: false,
                    completedAt = doc.getLong("completedAt")    ?: 0L,
                )
            }
    }

    override suspend fun completeChallenge(uid: String, challengeId: String, starReward: Int): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            userChallenges.document("${uid}_${challengeId}"),
            mapOf("completed" to true, "completedAt" to System.currentTimeMillis())
        )
        // FieldValue.increment is atomic — safe alongside concurrent session star saves
        batch.update(users.document(uid), "stars", FieldValue.increment(starReward.toLong()))
        batch.commit().await()
    }

    override suspend fun activateChallenge(uc: UserChallenge): Result<Unit> = runCatching {
        userChallenges.document("${uc.uid}_${uc.challengeId}").set(
            mapOf(
                "uid"         to uc.uid,
                "challengeId" to uc.challengeId,
                "startedAt"   to uc.startedAt,
                "baseReps"    to uc.baseReps,
                "baseStreak"  to uc.baseStreak,
                "completed"   to false,
                "completedAt" to 0L,
            )
        ).await()
    }

    override suspend fun abandonChallenge(uid: String, challengeId: String): Result<Unit> =
        runCatching { userChallenges.document("${uid}_${challengeId}").delete().await() }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    override suspend fun getTopUsers(limit: Int): Result<List<UserProfile>> = runCatching {
        // Fetch a few extra to cover any hidden admin accounts that will be stripped
        users
            .orderBy("stars", Query.Direction.DESCENDING)
            .limit((limit + ADMIN_USERNAMES.size).toLong())
            .get()
            .await()
            .mapNotNull { it.toUserProfile() }
            .filter { !ADMIN_USERNAMES.contains(it.username) }  // hide admin accounts
            .take(limit)
    }

    override suspend fun getProfilesByUids(uids: List<String>): Result<List<UserProfile>> = runCatching {
        if (uids.isEmpty()) return@runCatching emptyList()
        // Firestore whereIn supports up to 30 items; chunk for larger lists
        uids.chunked(30).flatMap { chunk ->
            users.whereIn("uid", chunk).get().await().mapNotNull { it.toUserProfile() }
        }
    }

    // ── Friends daily ranking ─────────────────────────────────────────────────

    override suspend fun getFriendsWithDailyReps(myUid: String, date: LocalDate): Result<List<FriendRankEntry>> = runCatching {
        // 1. Get my profile (contains friends list)
        val myProfile = users.document(myUid).get().await().toUserProfile()
            ?: return@runCatching emptyList()

        // 2. All participants = me + friends
        val allUids = (myProfile.friends + myUid).distinct()

        // 3. Fetch all profiles
        val profiles = if (allUids.size == 1) {
            listOf(myProfile)
        } else {
            getProfilesByUids(allUids).getOrDefault(emptyList())
        }

        val dateStr     = date.toString()
        val dailyLogs   = db.collection("dailyLogs")

        // 4. Fetch today's reps for everyone (batch by 30 for whereIn limit)
        val repsByUid = mutableMapOf<String, Int>()
        allUids.chunked(30).forEach { chunk ->
            val docIds = chunk.map { uid -> "${uid}_$dateStr" }
            docIds.forEach { docId ->
                try {
                    val snap = dailyLogs.document(docId).get().await()
                    if (snap.exists()) {
                        val uid = snap.getString("uid") ?: return@forEach
                        repsByUid[uid] = (snap.getLong("totalReps") ?: 0L).toInt()
                    }
                } catch (_: Exception) { /* keep 0 */ }
            }
        }

        // 5. Build ranking entries, sorted by reps desc
        profiles
            .map { p ->
                FriendRankEntry(
                    uid           = p.uid,
                    displayName   = p.displayName.ifBlank { p.username },
                    username      = p.username,
                    photoUrl      = p.photoUrl,
                    todayReps     = repsByUid[p.uid] ?: 0,
                    currentStreak = p.currentStreak,
                    tier          = RankSystem.tierFor(p.stars),
                    isMe          = p.uid == myUid,
                )
            }
            .sortedByDescending { it.todayReps }
    }

    // ── Inactivity penalty ────────────────────────────────────────────────────

    override suspend fun applyStarPenalty(uid: String, penalty: Long): Result<Unit> = runCatching {
        val currentStars = (users.document(uid).get().await().getLong("stars") ?: 0L)
        val newStars     = maxOf(0L, currentStars - penalty)
        users.document(uid).update("stars", newStars).await()
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid            = getString("uid") ?: id,
            username       = getString("username") ?: "",
            displayName    = getString("displayName") ?: "",
            photoUrl       = getString("photoUrl"),
            photoUpdatedAt = getLong("photoUpdatedAt") ?: 0L,
            stars          = getLong("stars") ?: 0L,
            totalReps      = getLong("totalReps") ?: 0L,
            currentStreak  = getLong("currentStreak")?.toInt() ?: 0,
            longestStreak  = getLong("longestStreak")?.toInt() ?: 0,
            friends        = (get("friends") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            height         = getDouble("height")?.toFloat(),
            weight         = getDouble("weight")?.toFloat(),
            dailyGoal           = getLong("dailyGoal")?.toInt() ?: 100,
            claimedAchievements = (get("claimedAchievements") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            equippedTags        = (get("equippedTags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        )
    }

    private fun UserProfile.toMap(): Map<String, Any?> = mapOf(
        "uid"            to uid,
        "username"       to username,
        "displayName"    to displayName,
        "photoUrl"       to photoUrl,
        "photoUpdatedAt" to photoUpdatedAt,
        "stars"          to stars,
        "totalReps"      to totalReps,
        "currentStreak"  to currentStreak,
        "longestStreak"  to longestStreak,
        "friends"        to friends,
        "height"         to height,
        "weight"         to weight,
        "dailyGoal"           to dailyGoal,
        "claimedAchievements" to claimedAchievements,
        "equippedTags"        to equippedTags,
        "createdAt"      to System.currentTimeMillis(),
    )
}
