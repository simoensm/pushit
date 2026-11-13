package com.pushitup.domain.repository

import com.pushitup.domain.model.FriendRankEntry
import com.pushitup.domain.model.FriendRequest
import com.pushitup.domain.model.UserChallenge
import com.pushitup.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface UserRepository {
    suspend fun createProfile(profile: UserProfile): Result<Unit>
    suspend fun getProfile(uid: String): Result<UserProfile>
    fun profileFlow(uid: String): Flow<UserProfile?>
    suspend fun updateProfile(uid: String, updates: Map<String, Any>): Result<Unit>
    /** Returns true if [username] is already used by someone other than [excludeUid]. */
    suspend fun isUsernameTaken(username: String, excludeUid: String? = null): Boolean
    suspend fun searchByUsername(query: String): Result<List<UserProfile>>
    /** Today's rep count for any user (0 if no session yet today). */
    suspend fun getTodayReps(uid: String): Int
    /**
     * Uploads [imageBytes] (JPEG) to Firebase Storage at photos/{uid}.jpg,
     * then saves the resulting download URL + a cache-bust timestamp to Firestore.
     * Returns the public download URL on success.
     */
    suspend fun uploadPhoto(uid: String, imageBytes: ByteArray): Result<String>
    /** Clears photoUrl and photoUpdatedAt from the profile (reverts to letter avatar). */
    suspend fun clearPhoto(uid: String): Result<Unit>
    suspend fun sendFriendRequest(fromUid: String, toUid: String): Result<Unit>
    suspend fun acceptFriendRequest(requestId: String, myUid: String, otherUid: String): Result<Unit>
    suspend fun declineFriendRequest(requestId: String): Result<Unit>
    fun incomingRequestsFlow(uid: String): Flow<List<FriendRequest>>
    /** Pending requests the current user sent (for showing "Pending" state in search). */
    fun outgoingRequestsFlow(uid: String): Flow<List<FriendRequest>>
    fun friendsFlow(uid: String): Flow<List<UserProfile>>
    suspend fun removeFriend(myUid: String, otherUid: String): Result<Unit>

    // Challenges
    fun activeChallengesFlow(uid: String): Flow<List<UserChallenge>>
    suspend fun getActiveChallenges(uid: String): Result<List<UserChallenge>>
    suspend fun activateChallenge(uc: UserChallenge): Result<Unit>
    suspend fun abandonChallenge(uid: String, challengeId: String): Result<Unit>
    /**
     * Atomically marks the challenge as completed AND credits [starReward] stars to the user
     * via FieldValue.increment — safe to call concurrently with session saves.
     */
    suspend fun completeChallenge(uid: String, challengeId: String, starReward: Int): Result<Unit>

    // Leaderboard
    suspend fun getTopUsers(limit: Int): Result<List<UserProfile>>
    suspend fun getProfilesByUids(uids: List<String>): Result<List<UserProfile>>

    /**
     * Subtract [penalty] stars from the user's profile (floor at 0).
     * Called on app launch when inactivity is detected.
     */
    suspend fun applyStarPenalty(uid: String, penalty: Long): Result<Unit>

    /**
     * Returns the current user + all their friends, each with today's rep count,
     * sorted descending by reps. The current user's entry has [FriendRankEntry.isMe] = true.
     */
    suspend fun getFriendsWithDailyReps(myUid: String, date: LocalDate): Result<List<FriendRankEntry>>
}
