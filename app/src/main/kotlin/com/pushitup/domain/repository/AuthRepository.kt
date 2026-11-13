package com.pushitup.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUid: String?
    val isLoggedIn: Boolean
    fun authStateFlow(): Flow<String?>          // emits uid when logged in, null when logged out
    suspend fun signUp(email: String, password: String): Result<String>   // returns uid
    suspend fun signIn(email: String, password: String): Result<String>
    suspend fun signInWithGoogle(idToken: String): Result<String>
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
    suspend fun reauthenticate(password: String): Result<Unit>
    suspend fun reauthenticateGoogle(idToken: String): Result<Unit>
    fun isGoogleUser(): Boolean
    suspend fun updateEmail(newEmail: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    fun currentEmail(): String?
    fun currentDisplayName(): String?
    fun currentPhotoUrl(): String?
}
