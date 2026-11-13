package com.pushitup.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.pushitup.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val currentUid: String?
        get() = auth.currentUser?.uid

    override val isLoggedIn: Boolean
        get() = auth.currentUser != null

    override fun authStateFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUp(email: String, password: String): Result<String> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user!!.uid
    }

    override suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user!!.uid
    }

    override suspend fun signInWithGoogle(idToken: String): Result<String> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        result.user!!.uid
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        auth.currentUser?.delete()?.await()
    }

    override suspend fun reauthenticate(password: String): Result<Unit> = runCatching {
        val user  = auth.currentUser ?: error("Not signed in")
        val email = user.email        ?: error("No email on account")
        val cred  = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(cred).await()
    }

    override suspend fun reauthenticateGoogle(idToken: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        user.reauthenticate(cred).await()
    }

    override fun isGoogleUser(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    override suspend fun updateEmail(newEmail: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        user.verifyBeforeUpdateEmail(newEmail).await()
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        user.updatePassword(newPassword).await()
    }

    override fun currentEmail(): String?       = auth.currentUser?.email
    override fun currentDisplayName(): String? = auth.currentUser?.displayName
    override fun currentPhotoUrl(): String?    = auth.currentUser?.photoUrl?.toString()
}
