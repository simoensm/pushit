package com.pushitup.core.update

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteVersionInfo(
    val latestVersionCode : Int    = 0,
    val latestVersionName : String = "",
)

/**
 * Reads version info from Firestore document  app_config/current
 *
 * You create/update this document manually in the Firebase console whenever
 * you publish a new build to the Play Store:
 *
 *   Collection : app_config
 *   Document   : current
 *   Fields     : latestVersionCode (number), latestVersionName (string)
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun fetchLatestVersion(): Result<RemoteVersionInfo> = runCatching {
        val doc = firestore
            .collection("app_config")
            .document("current")
            .get()
            .await()
        RemoteVersionInfo(
            latestVersionCode = (doc.getLong("latestVersionCode") ?: 0L).toInt(),
            latestVersionName = doc.getString("latestVersionName") ?: "",
        )
    }
}
