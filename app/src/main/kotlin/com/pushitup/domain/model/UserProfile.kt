package com.pushitup.domain.model

data class UserProfile(
    val uid            : String,
    val username       : String,
    val displayName    : String,
    val photoUrl       : String?,
    val photoUpdatedAt : Long    = 0L,  // timestamp written on each photo upload — used as Coil cache-buster
    val stars          : Long,          // 1 star/rep + streak/combo bonus
    val totalReps      : Long,
    val currentStreak  : Int,
    val longestStreak  : Int,
    val friends        : List<String>,
    val height         : Float?       = null,         // centimetres
    val weight         : Float?       = null,         // kilograms
    val dailyGoal           : Int          = 100,          // target reps per day
    /** Achievement IDs whose star reward has already been claimed by the user. */
    val claimedAchievements : List<String> = emptyList(),
    /** Tags currently shown on the public profile card, up to 3, e.g. "#IronMan". */
    val equippedTags        : List<String> = emptyList(),
) {
    /** URL to load in Coil — cache-busted so a new upload is always fetched fresh.
     *  Firebase Storage URLs already contain ?alt=media&token=..., so we append
     *  the cache-buster with & not ?.
     */
    val photoDisplayUrl: String?
        get() = if (!photoUrl.isNullOrBlank() && photoUpdatedAt > 0L)
            "$photoUrl&t=$photoUpdatedAt"
        else
            photoUrl
}
