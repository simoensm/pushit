package com.pushitup.domain.model

/** A single entry in today's friends daily ranking. */
data class FriendRankEntry(
    val uid           : String,
    val displayName   : String,
    val username      : String,
    val photoUrl      : String?,
    val todayReps     : Int,
    val currentStreak : Int,
    val tier          : RankTier,
    val isMe          : Boolean = false,
)
