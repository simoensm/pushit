package com.pushitup.domain.model

data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val fromDisplayName: String = "",
    val fromPhotoUrl: String? = null,
    val toUid: String = "",
    val status: String = "pending",   // "pending" | "accepted" | "declined"
    val createdAt: Long = 0L,
)
